package com.couchmate.util.mail

import scalatags.Text.all._

object Fragments {

  def email(content: Modifier*): Modifier = table(
    fontFamily := "Arial, Helvetica, sans-serif",
    width := "100%",
    height := "100%"
  )(content)

  def banner(title: String, subtitle: Option[String] = None): Seq[Modifier] = {
    if (subtitle.nonEmpty) {
      Seq(
        tr(td(
          height := "40px",
          padding := "10px 20px",
          backgroundColor := "#00414D",
          fontSize := "28px",
          fontWeight.bold,
          color.white
        )(title)),
        tr(td(
          height := "20px",
          padding := "10px 20px",
          backgroundColor := "#00414D",
          fontSize := "20px",
          color.white
        )(subtitle.get))
      )
    } else {
      Seq(tr(td(
        height := "40px",
        padding := "10px 20px",
        backgroundColor := "#00414D",
        fontSize := "28px",
        fontWeight.bold,
        color.white
      )(title)))
    }
  }

  def row(content: Modifier*): Modifier = tr(td(
    padding := "10px 20px"
  )(content))

  def emailText(text: String): Modifier =
    span(text)

  def emailLink(text: String, url: String): Modifier = a(
    href := url,
    marginBottom := "10px",
  )(text)

  def logo(h: Int, w: Int): Modifier = {
    import scalatags.Text.svgTags._
    import scalatags.Text.svgAttrs._
    svg(
      id := "couchmate",
      xmlns := "http://www.w3.org/2000/svg",
      viewBox := "0 0 1024 868.64",
      height := s"${h}px",
      width := s"${w}px"
    )(
      path(
        id := "tv",
        fill := "#f5f5f5",
        d := "M238.75,623C205,558.59,181.17,455.46,187.18,378.24c5.42-69.53,70.08-110.2,150.24-132.52l-88.78-183a31.36,31.36,0,1,1,26.92-14.28L378.27,226.41l67.5-116.91A31.37,31.37,0,1,1,473,123.78L423.22,228.86a865.81,865.81,0,0,1,89.18-6.6c136.67,3.17,316,38.13,325.23,156,6,77.84-18.38,182.5-52.54,246.64C622.52,580.31,400.6,578.39,238.75,623ZM512.4,251c-83.21,2-288.21,21.62-296.62,129.52-4.87,62.57,11.81,147.41,38.64,208.88,157-38,358.21-36.24,515.12,1.89,27.34-61.7,44.37-147.94,39.49-210.77C800.62,272.57,595.62,253,512.4,251Z"
      ),
      path(
        id := "couch",
        fill := "#f5f5f5",
        d := "M979.23,741.53c8.47-121.94,51.19-238.27,43.95-271.81-9.78-45.25-140.09-56.9-165.79-10.41C852.58,546.58,808,633.81,808,633.81s79.56,38.46,106.89,71.43c-86.75-59.7-245-91-402.91-91.88-157.93.85-316.16,32.18-402.91,91.88,27.33-33,106.89-71.43,106.89-71.43s-44.56-87.23-49.37-174.5c-25.7-46.49-156-34.84-165.79,10.41-7.24,33.54,35.48,149.87,44,271.81,8.33,119.86,85,127.11,167.44,127.11,100.42,0,200,0,299.79-.11s199.37.11,299.79.11C894.21,868.64,970.9,861.39,979.23,741.53Z"
      ),
      path(
        id := "pane4",
        fill := "#178cbe",
        d := "M688.72,298.69V553.36c23.16,3.52,45.83,7.71,67.8,12.58,21.26-55.17,35.21-128,30.87-183.79C784.18,341,741.11,315,688.72,298.69Z"
      ),
      path(
        id := "pane3",
        fill := "#69a711",
        d := "M524.16,273.07V540a1234.53,1234.53,0,0,1,141,10V292.15C614,279.41,558.58,274.56,524.16,273.07Z"
      ),
      path(
        id := "pane2",
        fill := "#fdc904",
        d := "M359.61,292.15V548.54a1205.15,1205.15,0,0,1,141-8.66V273.07C466.23,274.56,410.79,279.41,359.61,292.15Z"
      ),
      path(
        id := "pane1",
        fill := "#fd5900",
        d := "M336.08,551.68v-253C283.69,315,240.63,341,237.42,382.15c-4.33,55.45,9.28,127.07,30.13,181.9C289.75,559.19,312.67,555.07,336.08,551.68Z"
      )
    )
  }

}
