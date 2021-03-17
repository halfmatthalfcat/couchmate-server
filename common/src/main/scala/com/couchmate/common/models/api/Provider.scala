package com.couchmate.common.models.api

import com.couchmate.common.models.data.{ProviderType, Provider => InternalProvider}
import play.api.libs.json.{Json, OFormat}

case class Provider(
  providerId: Long,
  name: String,
  `type`: ProviderType,
  location: Option[String],
) extends Product with Serializable {
  implicit def fromInternalProvider(provider: InternalProvider): Provider = Provider(
    provider.providerId.get,
    provider.name,
    provider.`type`,
    provider.location
  )
}

object Provider {
  implicit val format: OFormat[Provider] = Json.format[Provider]
}
