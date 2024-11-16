# Social Media Center

Social Media Center is a multi-reader app for consuming feeds from Twitter/X, Mastodon and BlueSky. It is implemented in Kotlin Multiplatform for Android, iOS, Desktop and Web.

![Screenshot](screenshot.PNG?raw=true "Screenshot")

## Setup

Host the server component using Docker Compose. Adjust paths where needed:

```
services:
  socialmediaserver:
    container_name: socialmediaserver
    image: "ghcr.io/cbruegg/social-media-center:latest"
    volumes:
      - /home/ubuntu/docker/socialmediaserver/data:/data
    restart: unless-stopped
    ports:
      - "8005:8000"

  watchtower_socialmediaserver:
    image: containrrr/watchtower
    container_name: watchtower_socialmediaserver
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    command: socialmediaserver
```

Then place a `sources.json` into the `data` directory:

```json
{
  "mastodonFollowings": [
    {
      "username": "cbruegg",
      "server": "https://mastodon.green"
    }
  ],
  "blueskyFollowings": [
    {
      "username": "cbruegg.com",
      "server": "https://bsky.social",
      "password": "your account password - prefer using an App Password!"
    }
  ],
  "twitterLists": [
    {
      "id": "773867426563981312"
    }
  ]
}
```

For BlueSky, note that your username is probably something like `yourname.bsky.social` instead.

You can find Twitter list IDs in their URL, for example `https://x.com/i/lists/773867426563981312`.

Add an Nginx proxy in front of Social Media Center:

```
	server_name smc.yourdomain.com;

	location / {
		proxy_pass http://127.0.0.1:8005;
		gzip on;
		gzip_types text/plain application/javascript application/json application/wasm;
	}
```

You also need to set up HTTPS. [LetsEncrypt](https://certbot.eff.org/) is great for this.

If a service requires sign-in, the client app will walk the user through this process.

## Features & Usage

The app tries to be _just_ a reader. Tapping a post will take you to the corresponding native app of the service on your phone.
This avoids feature creep.

For security, the app will ask for the server's authentication token on first launch.
You can find it in the `token.txt` of the server's data directory.
In the example above, this would be `/home/ubuntu/docker/socialmediaserver/data/token.txt`.

## Architecture

As this was mainly a project for private use, the code is a mess 💩 Please don't judge 😇

- Feed retrieval happens only in the server-side component. The client loads a unified feed.
- The server-side component talks to the Mastodon client API and the Bluesky client API.
- The server-side component talks to Twitter through the [twikit](https://github.com/d60/twikit) scraper. This is because Twitter's official API is prohibitively expensive.
- To avoid account bans, the Twitter component does not fetch an account's news feed. Instead, it fetches the tweets of a defined Twitter list.
- Communication between server and client uses KotlinX Serialization using JSON.

## File Structure

These are the most important files:

- `/SocialMediaCenter`: Kotlin Multiplatform client and server component
- `/TWRSS`: Small script that uses Twikit and outputs JSON to consume from Kotlin
- `Dockerfile`: Builds and publishes the Docker image
