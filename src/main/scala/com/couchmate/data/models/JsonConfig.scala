package com.couchmate.data.models

import com.couchmate.util.json.CountryCodePlayJson
import play.api.libs.json.{JsonConfiguration, OptionHandlers}

private[models] trait JsonConfig extends CountryCodePlayJson {
  implicit val config: JsonConfiguration =
    JsonConfiguration(
      optionHandlers = OptionHandlers.WritesNull
    )
}
