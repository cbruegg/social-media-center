import time
from datetime import date, datetime
import os.path
import sys
import json
from typing import Optional

import twikit
from twikit import Client, Tweet
from dateutil import parser
from twikit.utils import Result

MAX_PAGES = 2
SECONDS_BETWEEN_PAGES = 5
CREDENTIALS_FILE = "credentials_twitter.json"
COOKIES_FILE = "cookies.json"


class BetterListSupportClient(Client):

    def get_list_tweets(self, list_id: str, count: int = 20, cursor: Optional[str] = None) -> Result[Tweet]:
        variables = {
            'listId': list_id,
            'count': count
        }
        if cursor is not None:
            variables['cursor'] = cursor
        params = {
            'variables': json.dumps(variables),
            'features': json.dumps(twikit.utils.FEATURES)
        }
        response = self.http.get(
            twikit.utils.Endpoint.LIST_LATEST_TWEETS,
            params=params,
            headers=self._base_headers
        ).json()

        items = twikit.utils.find_dict(response, 'entries')[0]
        next_cursor = items[-1]['content']['value']

        results = []
        for item in items:
            entry_id = item['entryId']
            if entry_id.startswith('tweet'):
                tweet_info = twikit.utils.find_dict(item, 'result')[0]
                if tweet_info['__typename'] == 'TweetWithVisibilityResults':
                    tweet_info = tweet_info['tweet']
                user_info = twikit.utils.find_dict(tweet_info, 'result')[0]
                results.append(Tweet(self, tweet_info, twikit.user.User(self, user_info)))
            elif entry_id.startswith("list-conversation"):
                items = twikit.utils.find_dict(item, 'items')
                # This kinda messes up the order, but we sort it in the Kotlin server anyway
                for item_idx in range(len(items[0])):
                    item = items[0][item_idx]['item']
                    tweet_info = twikit.utils.find_dict(item, 'result')[0]
                    if tweet_info['__typename'] == 'TweetWithVisibilityResults':
                        tweet_info = tweet_info['tweet']
                    user_info = twikit.utils.find_dict(tweet_info, 'result')[0]
                    results.append(Tweet(self, tweet_info, twikit.user.User(self, user_info)))

        return Result(
            results,
            lambda: self.get_list_tweets(list_id, count, next_cursor),
            next_cursor
        )


def main(list_id: str, data_path: str) -> None:
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
        client.user()
    except twikit.errors.Forbidden:
        client.login(auth_info_1=username, auth_info_2=email, password=password)

    client.save_cookies(cookies_path)

    feed_items = []

    # list_id = "1757481771950621108"
    tweets = client.get_list_tweets(list_id)
    for _ in range(MAX_PAGES):
        if len(tweets) == 0:
            break

        for tweet in filter(lambda tweet: tweet.user is not None, tweets):
            feed_items.append(tweet_to_feed_item(tweet))

        time.sleep(SECONDS_BETWEEN_PAGES)
        tweets = tweets.next()

    print(json.dumps(feed_items, default=json_serial))


def tweet_to_feed_item(tweet: twikit.Tweet):
    screen_name = tweet.user.screen_name
    link = f"https://twitter.com/{screen_name}/status/{tweet.id}"
    media_attachments = []
    if tweet.media is not None:
        for item in tweet.media:
            if item['type'] == 'photo':
                media_attachments.append({
                    'type': 'Image',
                    'previewImageUrl': item["media_url_https"],
                    'fullUrl': item["media_url_https"]
                })
            elif item['type'] == 'video':
                media_attachments.append({
                    'type': 'Video',
                    'previewImageUrl': item["media_url_https"],
                    'fullUrl': max(item['video_info']['variants'], key=lambda x: x.get('bitrate', -1))['url']
                })
    return {
        'text': tweet.text,
        'author': f"@{screen_name}",
        'authorImageUrl': tweet.user.profile_image_url,
        'id': tweet.id,
        'published': parser.parse(tweet.created_at),
        'link': link,
        'platform': 'Twitter',
        'repost': None,
        'mediaAttachments': media_attachments
    }


def json_serial(obj):
    if isinstance(obj, (datetime, date)):
        return obj.isoformat()
    raise TypeError("Type %s not serializable" % type(obj))


if __name__ == '__main__':
    main(list_id=sys.argv[1], data_path=sys.argv[2])
