from datetime import date, datetime
import os.path
import time
import sys
import json

import twikit
from twikit import Client
from dateutil import parser


def main(list_id: str, data_path: str) -> None:
    credentials_path = f"{data_path}/credentials.json"
    with open(credentials_path, "r") as file:
        loaded_data = json.load(file)

    username = loaded_data["username"]
    email = loaded_data["email"]
    password = loaded_data["password"]

    # Initialize client
    client = Client('en-US')

    cookies_path = f"{data_path}/cookies.json"
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
    for tweet in tweets:
        if tweet.user is None:
            pass

        feed_items.append(tweet_to_feed_item(tweet))

    time.sleep(5)

    tweets = tweets.next()
    for tweet in tweets:
        if tweet.user is None:
            pass

        feed_items.append(tweet_to_feed_item(tweet))

    print(json.dumps(feed_items, default=json_serial))


def tweet_to_feed_item(tweet: twikit.Tweet):
    name = tweet.user.name
    link = f"https://twitter.com/{name}/status/{tweet.id}"
    return {
        'text': tweet.text,
        'author': name,
        'id': tweet.id,
        'published': parser.parse(tweet.created_at),
        'link': link,
        'platform': 'Twitter'
    }


def json_serial(obj):
    """JSON serializer for objects not serializable by default json code"""

    if isinstance(obj, (datetime, date)):
        return obj.isoformat()
    raise TypeError("Type %s not serializable" % type(obj))


if __name__ == '__main__':
    main(list_id=sys.argv[1], data_path=sys.argv[2])
