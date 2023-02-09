# Couchmate Server

Scala/Akka-powered backend for Couchmate.

> This repository is an old version of Couchmate that I wrote in 2020. I've written Couchmate at least 6 times and this was the second go-around in Scala. It ran in production for about a year until I ended up redoing it in Typescript (for a second time), which is currently in production at https://couchmate.com.

## Stack

High Level:
* Scala 2.13
* Akka Typed 2.6.5
  - Streams
  - Cluster/Sharding/Singletons
  - Persistence
  - Kubernetes Bootstrapping (Akka Management)
* Akka HTTP 10.x
* Slick 3.3.2
* Emitted Typescript Types for Case Classes via [scala2ts](https://github.com/scala2ts), a project I wrote specifically for this iteration of Couchmate and the accompanying React Native app.

## High Level Operation

Couchmate runs two sets of persistent Actors - [Users](https://github.com/halfmatthalfcat/couchmate-server/blob/master/core/src/main/scala/com/couchmate/util/akka/extensions/UserExtension.scala) and [Rooms](https://github.com/halfmatthalfcat/couchmate-server/blob/master/core/src/main/scala/com/couchmate/util/akka/extensions/RoomExtension.scala). These Actors are sharded within the cluster. 

Whenever a user [connects](https://github.com/halfmatthalfcat/couchmate-server/blob/master/core/src/main/scala/com/couchmate/api/Routes.scala#L74) to the backend (via Websockets) a Persistent Actor is either created or "woken up" and handles all interaction with the cluster. Rooms are created, however depending on the state of the show, will auto-close or "open" to allow chatting between all the connected sockets. Both Users and Rooms are one bit FSM, that react to Websocket messages that are recieved.

## Database

Couchmate runs on Postgres and leverages Slick/Slick Migration API to interface with the database and run reproducable migrations. I actually wrote a [pesudo-framework](https://github.com/halfmatthalfcat/couchmate-server/tree/master/migration/src/main/scala/com/couchmate/migration/db) to manage migrations since the available options for Scala aren't great.

I _was_ using Akka Streams + Slick at one point but it became a pain to reason about as things progressed, so I fellback to the Future-based Slick access pattern.
