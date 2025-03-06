import asyncio
import time
from datetime import date, datetime
import os.path
import sys
import json
from functools import partial
from typing import Optional

import twikit
from twikit import Client, Tweet
from dateutil import parser
from twikit.media import Photo, Video
from twikit.tweet import tweet_from_data
from twikit.utils import Result, find_dict

MAX_PAGES = 2
SECONDS_BETWEEN_PAGES = 5
CREDENTIALS_FILE = "credentials_twitter.json"
COOKIES_FILE = "cookies.json"


class BetterListSupportClient(Client):

    async def get_list_tweets(self, list_id: str, count: int = 20, cursor: Optional[str] = None) -> Result[Tweet]:
        response, _ = await self.gql.list_latest_tweets_timeline(list_id, count, cursor)

        items_ = find_dict(response, 'entries', find_one=True)
        if not items_:
            raise ValueError(f'Invalid list id: {list_id}')
        items = items_[0]
        next_cursor = items[-1]['content']['value']

        results = []
        for item in items:
            entry_id = item['entryId']
            if entry_id.startswith('tweet'):
                tweet = tweet_from_data(self, item)
                if tweet is not None:
                    results.append(tweet)
            elif entry_id.startswith("list-conversation"):
                items = twikit.utils.find_dict(item, 'items')
                # This kinda messes up the order, but we sort it in the Kotlin server anyway
                for item_idx in range(len(items[0])):
                    item = items[0][item_idx]['item']
                    tweet = tweet_from_data(self, item)
                    if tweet is not None:
                        results.append(tweet)

        return Result(
            results,
            partial(self.get_list_tweets, list_id, count, next_cursor),
            next_cursor
        )


async def main(list_id: str, data_path: str) -> None:
    credentials_path = f"{data_path}/{CREDENTIALS_FILE}"
    with open(credentials_path, "r") as file:
        loaded_data = json.load(file)

    username = loaded_data["username"]
    email = loaded_data["email"]
    password = loaded_data["password"]

    # Initialize client
    client = BetterListSupportClient('en-US')

    cookies_path = f"{data_path}/{COOKIES_FILE}"
    if os.path.isfile(cookies_path):
        client.load_cookies(cookies_path)

    try:
        # Check if we can successfully fetch user data with loaded cookies
        await client.user()
    except twikit.errors.Forbidden or twikit.errors.NotFound:
        await client.login(auth_info_1=username, auth_info_2=email, password=password)

    client.save_cookies(cookies_path)

    feed_items = []

    # list_id = "1757481771950621108"
    tweets = await client.get_list_tweets(list_id)
    for _ in range(MAX_PAGES):
        if len(tweets) == 0:
            break

        for tweet in filter(lambda tweet: tweet.user is not None, tweets):
            feed_items.append(tweet_to_feed_item(tweet))

        time.sleep(SECONDS_BETWEEN_PAGES)
        tweets = await tweets.next()

    print(json.dumps(feed_items, default=json_serial))


def tweet_to_feed_item(tweet: twikit.Tweet):
    screen_name = tweet.user.screen_name
    link = f"https://twitter.com/{screen_name}/status/{tweet.id}"
    media_attachments = []
    if tweet.media is not None:
        for item in tweet.media:
            if item is Photo:
                media_attachments.append({
                    'type': 'Image',
                    'previewImageUrl': item.media_url,
                    'fullUrl': item.media_url
                })
            elif item is Video:
                media_attachments.append({
                    'type': 'Video',
                    'previewImageUrl': item.media_url,
                    'fullUrl': max(item.video_info['variants'], key=lambda x: x.get('bitrate', -1))['url'],
                })
    return {
        'text': tweet.text,
        'author': f"@{screen_name}",
        'authorImageUrl': tweet.user.profile_image_url,
        'id': tweet.id,
        'published': parser.parse(tweet.created_at),
        'link': link,
        'platform': 'Twitter',
        'mediaAttachments': media_attachments,
        'quotedPost': tweet_to_feed_item(tweet.quote) if tweet.quote is not None else None,
        'repostMeta': None
    }


def json_serial(obj):
    if isinstance(obj, (datetime, date)):
        return obj.isoformat()
    raise TypeError("Type %s not serializable" % type(obj))


if __name__ == '__main__':
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        asyncio.run(main(list_id=sys.argv[1], data_path=sys.argv[2]))
    except KeyboardInterrupt:
        pass
