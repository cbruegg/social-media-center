import time
from datetime import date, datetime
import os.path
import sys
import json

import twikit
from twikit import Client
from dateutil import parser

MAX_PAGES = 2
SECONDS_BETWEEN_PAGES = 5
CREDENTIALS_FILE = "credentials.json"
COOKIES_FILE = "cookies.json"


def main(list_id: str, data_path: str) -> None:
    credentials_path = f"{data_path}/{CREDENTIALS_FILE}"
    with open(credentials_path, "r") as file:
        loaded_data = json.load(file)

    username = loaded_data["username"]
    email = loaded_data["email"]
    password = loaded_data["password"]

    # Initialize client
    client = Client('en-US')

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
    name = tweet.user.name
    screen_name = tweet.user.screen_name
    link = f"https://twitter.com/{screen_name}/status/{tweet.id}"
    return {
        'text': tweet.text,
        'author': f"@{screen_name}",
        'authorImageUrl': tweet.user.profile_image_url,
        'id': tweet.id,
        'published': parser.parse(tweet.created_at),
        'link': link,
        'platform': 'Twitter'
    }


def json_serial(obj):
    if isinstance(obj, (datetime, date)):
        return obj.isoformat()
    raise TypeError("Type %s not serializable" % type(obj))


if __name__ == '__main__':
    main(list_id=sys.argv[1], data_path=sys.argv[2])
