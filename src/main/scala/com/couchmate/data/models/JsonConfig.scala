package com.couchmate.data.models

import play.api.libs.json.{JsonConfiguration, OptionHandlers}

private[models] trait JsonConfig {
  implicit val config: JsonConfiguration =
    JsonConfiguration(
      optionHandlers = OptionHandlers.WritesNull
    )
}
