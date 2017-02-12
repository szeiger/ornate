package com.novocode.ornate

import java.util.Locale

/** CSS-compatible color representations in RGBA and HSLA spaces.
  * All operations are defined in the color spaces that perform them. There are no automatic conversions. */
sealed trait Color {
  type Self <: Color
  def toRGB: RGBColor
  def toHSL: HSLColor
  def alpha: Float
  final def alphaByte: Int = (alpha*255f+0.5f).toInt
  def toCSSString: String
  def withAlpha(a: Float): Self
  final def opaque: Self = withAlpha(1.0f)
}

object Color {
  val Black = new RGBColor(0, 0, 0, 1)
  val White = new RGBColor(1, 1, 1, 1)
  val Transparent = new RGBColor(1, 1, 1, 0)

  private val RGB = "^rgb\\(\\s*([^,\\s]*)\\s*,\\s*([^,\\s]*)\\s*,\\s*([^,\\s]*)\\s*\\)$".r
  private val RGBA = "^rgba\\(\\s*([^,\\s]*)\\s*,\\s*([^,\\s]*)\\s*,\\s*([^,\\s]*)\\s*,\\s*([^,\\s]*)\\s*\\)$".r
  private val HSL = "^hsl\\(\\s*([^,\\s]*)\\s*,\\s*([^,\\s]*)\\s*,\\s*([^,\\s]*)\\s*\\)$".r
  private val HSLA = "^hsla\\(\\s*([^,\\s]*)\\s*,\\s*([^,\\s]*)\\s*,\\s*([^,\\s]*)\\s*,\\s*([^,\\s]*)\\s*\\)$".r
  private val HTML = "^#([\\p{XDigit}]{2})([\\p{XDigit}]{2})([\\p{XDigit}]{2})$".r
  private val HTMLA = "^#([\\p{XDigit}]{2})([\\p{XDigit}]{2})([\\p{XDigit}]{2})([\\p{XDigit}]{2})$".r
  private val HTML_NARROW = "^#(\\p{XDigit})(\\p{XDigit})(\\p{XDigit})$".r
  private val HTMLA_NARROW = "^#(\\p{XDigit})(\\p{XDigit})(\\p{XDigit})(\\p{XDigit})$".r

  /** Parse a CSS color name. All CSS 3 keywords, rgb/rgba/hsl/hsla notations and hex notations are supported. */
  def parse(s: String): Color = {
    def hex(s: String): Float = Integer.parseInt(s, 16).toFloat / 255.0f
    def scalar(s: String, rawDiv: Float, percentDiv: Float): Float = {
      val t = s.trim
      if(t.endsWith("%")) {
        if(rawDiv != 0.0) java.lang.Float.parseFloat(t.dropRight(1).trim) / percentDiv
        else throw new IllegalArgumentException("No percentage values allowed in \""+t+"\"")
      } else {
        if(rawDiv != 0.0) java.lang.Float.parseFloat(t) / rawDiv
        else throw new IllegalArgumentException("Only percentage values allowed in \""+t+"\"")
      }
    }
    s.trim.toLowerCase(Locale.US) match {
      case "transparent" => Transparent
      case RGB(r, g, b) => new RGBColor(scalar(r, 255, 100), scalar(g, 255, 100), scalar(b, 255, 100), 1)
      case RGBA(r, g, b, a) => new RGBColor(scalar(r, 255, 100), scalar(g, 255, 100), scalar(b, 255, 100), scalar(a, 1, 100))
      case HSL(h, s, l) => new HSLColor(scalar(h, 1, 0), scalar(s, 0, 100), scalar(l, 0, 100), 1)
      case HSL(h, s, l, a) => new HSLColor(scalar(h, 1, 0), scalar(s, 0, 100), scalar(l, 0, 100), scalar(a, 1, 100))
      case HTML(r, g, b) => new RGBColor(hex(r), hex(g), hex(b), 1)
      case HTMLA(r, g, b, a) => new RGBColor(hex(r), hex(g), hex(b), hex(a))
      case HTML_NARROW(r, g, b) => new RGBColor(hex(r) * 17, hex(g) * 17, hex(b) * 17, 1)
      case HTMLA_NARROW(r, g, b, a) => new RGBColor(hex(r) * 17, hex(g) * 17, hex(b) * 17, hex(a) * 17)
      case s => keywords.get(s) match {
        case Some(h) => new RGBColor(hex(h.substring(0, 2)), hex(h.substring(2, 4)), hex(h.substring(4, 6)), 1)
        case None =>
          throw new IllegalArgumentException("Not a legal color value: \""+s+"\"")
      }
    }
  }

  // From CSS Color Module Level 4 draft, 12 February 2017 (https://drafts.csswg.org/css-color/)
  private val keywords = Map(
    "aliceblue" -> "f0f8ff",
    "antiquewhite" -> "faebd7",
    "aqua" -> "00ffff",
    "aquamarine" -> "7fffd4",
    "azure" -> "f0ffff",
    "beige" -> "f5f5dc",
    "bisque" -> "ffe4c4",
    "black" -> "000000",
    "blanchedalmond" -> "ffebcd",
    "blue" -> "0000ff",
    "blueviolet" -> "8a2be2",
    "brown" -> "a52a2a",
    "burlywood" -> "deb887",
    "cadetblue" -> "5f9ea0",
    "chartreuse" -> "7fff00",
    "chocolate" -> "d2691e",
    "coral" -> "ff7f50",
    "cornflowerblue" -> "6495ed",
    "cornsilk" -> "fff8dc",
    "crimson" -> "dc143c",
    "cyan" -> "00ffff",
    "darkblue" -> "00008b",
    "darkcyan" -> "008b8b",
    "darkgoldenrod" -> "b8860b",
    "darkgray" -> "a9a9a9",
    "darkgreen" -> "006400",
    "darkgrey" -> "a9a9a9",
    "darkkhaki" -> "bdb76b",
    "darkmagenta" -> "8b008b",
    "darkolivegreen" -> "556b2f",
    "darkorange" -> "ff8c00",
    "darkorchid" -> "9932cc",
    "darkred" -> "8b0000",
    "darksalmon" -> "e9967a",
    "darkseagreen" -> "8fbc8f",
    "darkslateblue" -> "483d8b",
    "darkslategray" -> "2f4f4f",
    "darkslategrey" -> "2f4f4f",
    "darkturquoise" -> "00ced1",
    "darkviolet" -> "9400d3",
    "deeppink" -> "ff1493",
    "deepskyblue" -> "00bfff",
    "dimgray" -> "696969",
    "dimgrey" -> "696969",
    "dodgerblue" -> "1e90ff",
    "firebrick" -> "b22222",
    "floralwhite" -> "fffaf0",
    "forestgreen" -> "228b22",
    "fuchsia" -> "ff00ff",
    "gainsboro" -> "dcdcdc",
    "ghostwhite" -> "f8f8ff",
    "gold" -> "ffd700",
    "goldenrod" -> "daa520",
    "gray" -> "808080",
    "green" -> "008000",
    "greenyellow" -> "adff2f",
    "grey" -> "808080",
    "honeydew" -> "f0fff0",
    "hotpink" -> "ff69b4",
    "indianred" -> "cd5c5c",
    "indigo" -> "4b0082",
    "ivory" -> "fffff0",
    "khaki" -> "f0e68c",
    "lavender" -> "e6e6fa",
    "lavenderblush" -> "fff0f5",
    "lawngreen" -> "7cfc00",
    "lemonchiffon" -> "fffacd",
    "lightblue" -> "add8e6",
    "lightcoral" -> "f08080",
    "lightcyan" -> "e0ffff",
    "lightgoldenrodyellow" -> "fafad2",
    "lightgray" -> "d3d3d3",
    "lightgreen" -> "90ee90",
    "lightgrey" -> "d3d3d3",
    "lightpink" -> "ffb6c1",
    "lightsalmon" -> "ffa07a",
    "lightseagreen" -> "20b2aa",
    "lightskyblue" -> "87cefa",
    "lightslategray" -> "778899",
    "lightslategrey" -> "778899",
    "lightsteelblue" -> "b0c4de",
    "lightyellow" -> "ffffe0",
    "lime" -> "00ff00",
    "limegreen" -> "32cd32",
    "linen" -> "faf0e6",
    "magenta" -> "ff00ff",
    "maroon" -> "800000",
    "mediumaquamarine" -> "66cdaa",
    "mediumblue" -> "0000cd",
    "mediumorchid" -> "ba55d3",
    "mediumpurple" -> "9370db",
    "mediumseagreen" -> "3cb371",
    "mediumslateblue" -> "7b68ee",
    "mediumspringgreen" -> "00fa9a",
    "mediumturquoise" -> "48d1cc",
    "mediumvioletred" -> "c71585",
    "midnightblue" -> "191970",
    "mintcream" -> "f5fffa",
    "mistyrose" -> "ffe4e1",
    "moccasin" -> "ffe4b5",
    "navajowhite" -> "ffdead",
    "navy" -> "000080",
    "oldlace" -> "fdf5e6",
    "olive" -> "808000",
    "olivedrab" -> "6b8e23",
    "orange" -> "ffa500",
    "orangered" -> "ff4500",
    "orchid" -> "da70d6",
    "palegoldenrod" -> "eee8aa",
    "palegreen" -> "98fb98",
    "paleturquoise" -> "afeeee",
    "palevioletred" -> "db7093",
    "papayawhip" -> "ffefd5",
    "peachpuff" -> "ffdab9",
    "peru" -> "cd853f",
    "pink" -> "ffc0cb",
    "plum" -> "dda0dd",
    "powderblue" -> "b0e0e6",
    "purple" -> "800080",
    "rebeccapurple" -> "663399",
    "red" -> "ff0000",
    "rosybrown" -> "bc8f8f",
    "royalblue" -> "4169e1",
    "saddlebrown" -> "8b4513",
    "salmon" -> "fa8072",
    "sandybrown" -> "f4a460",
    "seagreen" -> "2e8b57",
    "seashell" -> "fff5ee",
    "sienna" -> "a0522d",
    "silver" -> "c0c0c0",
    "skyblue" -> "87ceeb",
    "slateblue" -> "6a5acd",
    "slategray" -> "708090",
    "slategrey" -> "708090",
    "snow" -> "fffafa",
    "springgreen" -> "00ff7f",
    "steelblue" -> "4682b4",
    "tan" -> "d2b48c",
    "teal" -> "008080",
    "thistle" -> "d8bfd8",
    "tomato" -> "ff6347",
    "turquoise" -> "40e0d0",
    "violet" -> "ee82ee",
    "wheat" -> "f5deb3",
    "white" -> "ffffff",
    "whitesmoke" -> "f5f5f5",
    "yellow" -> "ffff00",
    "yellowgreen" -> "9acd32"
  )
}

final case class RGBColor(red: Float /* 0..1 */, green: Float /* 0..1 */, blue: Float /* 0..1 */, alpha: Float) extends Color {
  if(red<0 || red>1 || green<0 || green>1 || blue<0 || blue>1 || alpha<0 || alpha>1) throw new IllegalArgumentException

  type Self = RGBColor

  def redByte: Int = (red*255f+0.5f).toInt
  def greenByte: Int = (green*255f+0.5f).toInt
  def blueByte: Int = (blue*255f+0.5f).toInt

  def toCSSString: String =
    if(alpha == 0.0) "transparent"
    else if(alpha == 1.0) s"rgb($redByte, $greenByte, $blueByte)"
    else s"rgba($redByte, $greenByte, $blueByte, $alpha)"

  def toHTMLString: String = {
    def hex(i: Int): String = {
      val s = Integer.toHexString(i)
      if(s.length < 2) "0" + s else s
    }
    val a = hex(alphaByte)
    if(a == "ff") "#" + hex(redByte) + hex(greenByte) + hex(blueByte)
    else "#" + hex(redByte) + hex(greenByte) + hex(blueByte) + hex(alphaByte)
  }

  def toRGB: RGBColor = this

  // based on https://tips4java.wordpress.com/2009/07/05/hsl-color/ (Public Domain)
  def toHSL: HSLColor = {
    val min = math.min(red, math.min(green, blue))
    val max = math.max(red, math.max(green, blue))
    val h: Float =
      if(max == min) 0
      else if(max == red) ((60 * (green - blue) / (max - min)) + 360) % 360
      else if(max == green) (60 * (blue - red) / (max - min)) + 120
      else if(max == blue) (60 * (red - green) / (max - min)) + 240
      else 0
    val l: Float = (max + min) / 2
    val s: Float =
      if(max == min) 0
      else if(l <= 0.5f) (max - min) / (max + min)
      else (max - min) / (2 - max - min)
    new HSLColor(h, s * 100, l * 100, alpha)
  }

  def withBackground(c: RGBColor): RGBColor =
    if(alpha == 1.0 && c.alpha == 1.0) this
    else {
      val back: Float = 1.0f - alpha
      val r = red*alpha + c.red*back
      val g = green*alpha + c.green*back
      val b = blue*alpha + c.blue*back
      new RGBColor(r, g, b, c.alpha)
    }

  def withAlpha(a: Float): RGBColor = if(alpha == a) this else copy(alpha = a)
}

final case class HSLColor(hue: Float /* 0..360 */, saturation: Float /* 0..100 */, lightness: Float /* 0..100 */, alpha: Float) extends Color {
  if(saturation<0 || saturation>100 || lightness<0 || lightness>100 || alpha<0 || alpha>1) throw new IllegalArgumentException

  type Self = HSLColor

  def complementary: HSLColor = copy(hue = (hue + 180.0f) % 360.0f)

  def darker(percent: Float): HSLColor =
    copy(lightness = math.max(0.0f, lightness * ((100.0f - percent) / 100.0f)))

  def lighter(percent: Float): HSLColor =
    copy(lightness = math.min(100.0f, lightness * ((100.0f + percent) / 100.0f)))

  // based on https://tips4java.wordpress.com/2009/07/05/hsl-color/ (Public Domain)
  def toRGB: RGBColor = {
    def hueToRGB(p: Float, q: Float, _h: Float): Float = {
      var h = _h
      if(h < 0) h += 1
      if(h > 1) h -= 1
      if(6 * h < 1) p + ((q - p) * 6 * h)
      else if(2 * h < 1 ) q
      else if(3 * h < 2) p + ((q - p) * 6 * ((2.0f / 3.0f) - h))
      else p
    }

    val h = (hue % 360.0f) / 360.0f
    val s = saturation / 100.0f
    val l = lightness / 100.0f
    val q: Float = if(l < 0.5) l * (1 + s) else (l + s) - (s * l)
    val p: Float = 2 * l - q
    val r: Float = math.min(math.max(0, hueToRGB(p, q, h + (1.0f / 3.0f))), 1.0f)
    val g: Float = math.min(math.max(0, hueToRGB(p, q, h)), 1.0f)
    val b: Float = math.min(math.max(0, hueToRGB(p, q, h - (1.0f / 3.0f))), 1.0f)
    new RGBColor(r, g, b, alpha)
  }

  def toHSL: HSLColor = this

  def toCSSString: String =
    if(alpha == 0.0) "transparent"
    else if(alpha == 1.0) s"hsl($hue, $saturation%, $lightness%)"
    else s"hsla($hue, $saturation%, $lightness%, $alpha)"

  def withAlpha(a: Float): HSLColor = if(alpha == a) this else copy(alpha = a)
}
