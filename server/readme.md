# Getting Started

Visit to see REST api docs.

```bash
http://localhost:8080/swagger
```

or for json representaition

```
http://localhost:8080/v3/api-docs
```

Meaning:
> __request__ = websocket message __player -> server__\
> __message__ = websocket message __server -> player__

Run everything:

```bash
docker compose up -d --build --force-recreate
```

## Connection

There is few connection options available.\
Base url is: `ws://<host>:<port>/game`\
and possible query parameters:

- `user: string`: player username to be registered in the game
- `player (optional): string`: game assigned playerId, if known, server is able to reconnect player to the original game
- `lobby (optional): string`: lobby name to connect to
- `new: boolean`: whether to create new lobby, if `false` connect to the existing lobby, defaults to `false`
- `private: boolean`: whether the newly created lobby is private (meaning players need to know its name to connect)
  or public, defaults to `false`

## websocket requests

### Move requests

```json
{
  "requestType": "MOVE",
  "move": {
    "moveType": "DRAW"
  }
}
```

```json
{
  "requestType": "MOVE",
  "move": {
    "moveType": "PASS"
  }
}
```

```json
{
  "requestType": "MOVE",
  "move": {
    "moveType": "PLAY",
    "card": {
      "color": "HEARTS",
      "type": "SEVEN"
    }
  }
}
```

```json
{
  "requestType": "MOVE",
  "move": {
    "moveType": "PLAY",
    "card": {
      "color": "HEARTS",
      "type": "QUEEN"
    },
    "nextColor": "SPADES"
  }
}
```

### Control requests

#### Ready/Unready lobby request

```json
{
  "requestType": "CONTROL",
  "control": {
    "controlType": "READY"
  }
}
```

```json
{
  "requestType": "CONTROL",
  "control": {
    "controlType": "UNREADY"
  }
}
```

#### NPC adding and removing

```json
{
  "requestType": "CONTROL",
  "control": {
    "controlType": "REGISTER_NPC"
  }
}
```

#### Kick player from lobby (only for lobby owner)

```json
{
  "requestType": "CONTROL",
  "control": {
    "controlType": "KICK",
    "username": "Bayraktar"
  }
}
```

#### Cheat End Game Instantly (MAU_CHEATING_ENABLED=true)

```json
{
  "requestType": "CONTROL",
  "control": {
    "controlType": "CHEAT_END"
  }
}
```

#### Chat

```json
{
  "requestType": "CHAT",
  "chat": {
    "chatType": "MESSAGE",
    "message": "Hello World!"
  }
}
```

```json
{
  "requestType": "CHAT",
  "chat": {
    "chatType": "HISTORY"
  }
}
```

## Messages

### type: __ACTION__

#### Ready/Unready

```json
{
  "messageType": "ACTION",
  "action": {
    "type": "READY",
    "username": "joe"
  }
}
```

```json
{
  "messageType": "ACTION",
  "action": {
    "type": "UNREADY",
    "username": "joe"
  }
}
```

### type: __ERROR__

```json
{
  "messageType": "ERROR",
  "exceptionBody": {
    "name": "ExceptionClassName",
    "message": "This is an exception message.",
    "timestamp": "2025-08-29T07:36:33.204362Z"
  }
}
```

### type: __SERVER_MESSAGE__

```json
{
  "body": {
    "username": "joe",
    "message": "Hello world",
    "bodyType": "CHAT"
  },
  "messageType": "SERVER_MESSAGE"
}
```

## How to work with __Authentication__

REST endpoints related to authentication are on path `/api/auth/*`.
Auth plays a role in websocket handshake. If any auth error occurs, handshake is completed successfully,
error message is sent through the websocket session and session is immediatelly closed.\
Common game authentication workflow may look as following

- (register) login the user `POST /api/auth/register` and `POST /api/auth/login/`
    - tokens are stored in the _httpOnly_ cookies
- try to open websocket connection (`user` query param can be omitted, since the authentication identity is represented
  with the jwt token)
- if success -> play the game
- if you receive error as the first message with name `AuthExpiredException`, that means jwt expired and needs to be
  refreshed.
  ```json
  {
    "exceptionBody": {
        "name": "AuthExpiredException",
        "message": "expired ...",
        "timestamp": "2026-01-25T19:51:47.396389Z"
    },
    "messageType": "ERROR"
  }
  ```
- call endpoint `POST /api/auth/refresh`
    - your refresh token stored in cookies is automatically sent with the request (if any)
    - operation generates new tokens and store them in the cookies again
- retry websocket connection

### Good to know

- Retrieve jwt remaining time until expiry:
  > GET /api/time-left
- Logout
    - this operation removes all the auth cookies
  > POST /api/auth/logout
