package com.couchmate.common.models.data

import com.couchmate.common.util.json.CountryCodePlayJson
import play.api.libs.json.{JsonConfiguration, OptionHandlers}

private[models] trait JsonConfig extends CountryCodePlayJson {
  implicit val config: JsonConfiguration =
    JsonConfiguration(
      optionHandlers = OptionHandlers.WritesNull
    )
}
