package com.couchmate.api

import akka.http.scaladsl.server.PathMatcher1
import com.couchmate.services.gracenote.listing.ListingPullType

trait PathUtils {
  implicit class ListingPullTypeSegment(segment: PathMatcher1[Int]) {
    def as: PathMatcher1[ListingPullType] = segment.flatMap(ListingPullType.withValueOpt)
  }
}
