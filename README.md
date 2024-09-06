# Social Media Center

Social Media Center is a multi-reader app for consuming feeds from Twitter/X, Mastodon and BlueSky. It is implemented in Kotlin Multiplatform for Android, iOS, Desktop and Web.

![Screenshot]screenshot.jpg?raw=true "Screenshot")

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

  skybridge:
    container_name: "skybridge"
    image: videah/skybridge:latest
    restart: always
    volumes:
      - skybridge:/app/database
    ports:
      - "8006:8080"
    environment:
      # Base URL of where SkyBridge will be hosted without the protocol.
      - SKYBRIDGE_BASEURL=your-skybridge-instance.com
      # Random secret, generate with `openssl rand -base64 32`.
      - SKYBRIDGE_SECRET=PUT_SECRET_HERE
      # Password used to make a SkyBridge instance private.
      - SKYBRIDGE_AUTH_PASSWORD=SOME_PASSWORD
      # Should a bridge password be required to authenticate?
      - SKYBRIDGE_REQUIRE_AUTH_PASSWORD=true
      # Should a nice index page be shown on the root URL?
      - SKYBRIDGE_SHOW_INDEX=false
      # Allow backfilling/scrolling on timelines? (can cause issues on instances under heavy load)
      - SKYBRIDGE_ALLOW_BACKFILL=true

  watchtower_skybridge:
    image: containrrr/watchtower
    container_name: watchtower_skybridge
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    command: skybridge

volumes:
  skybridge:
```

Then place a `sources.json` into the `data` directory:

```json
{
  "mastodonFollowings": [
    {
      "username": "cbruegg",
      "server": "https://mastodon.green"
    },
    {
      "username": "cbruegg.bsky.social",
      "server": "https://your-skybridge-instance.com"
    }
  ],
  "twitterLists": [
    {
      "id": "773867426563981312"
    }
  ]
}
```

You can find Twitter list IDs in their URL, for example `https://x.com/i/lists/773867426563981312`.

Add an Nginx proxy in front of Social Media Center and SkyBridge:

```
	server_name smc.yourdomain.com;

	location / {
		proxy_pass http://127.0.0.1:8005;
		gzip on;
		gzip_types text/plain application/javascript application/json application/wasm;
	}
```

```
	server_name your-skybridge-instance.com;

	location / {
		proxy_pass http://127.0.0.1:8006;
		gzip on;
		gzip_types text/plain application/javascript application/json application/wasm;
	}
```

You also need to set up HTTPS. [LetsEncrypt](https://certbot.eff.org/) is great for this.

If a service requires sign-in, the client app will walk the user through this process.

## Features & Usage

TODO.

## Architecture

As this was mainly a project for private use, the code is a bit of a mess.

- Feed retrieval happens only in the server-side component. The client loads a unified feed.
- The server-side component talks to the Mastodon client API. By leveraging [SkyBridge](https://github.com/videah/SkyBridge), connecting to BlueSky is also possible.
- The server-side component talks to Twitter through the [twikit](https://github.com/d60/twikit) scraper. This is because Twitter's official API is prohibitively expensive.
- To avoid account bans, the Twitter component does not fetch an account's news feed. Instead, it fetches the tweets of a defined Twitter list.
- Communication between server and client uses KotlinX Serialization using JSON.

## File Structure

TODO.