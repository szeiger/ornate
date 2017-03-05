package com.novocode.ornate

import java.net.URI

import better.files._
import com.novocode.ornate.config.Global
import org.junit.Test
import org.junit.Assert._

class EmojiTest {
  val global = new Global(file"../doc", None)
  val emoji = new EmojiParserExtension(global.referenceConfig.objectKind("extension").create("emoji"))
  val uri = new URI("webjar:/emojione/assets/svg/1f329.svg")
  val u = (Character.toChars(0x1f329) ++ Character.toChars(0xfe0f)).mkString

  @Test def testEmojiOne: Unit = {
    emoji.emojiOneData.foreach(println _)
    assertEquals(Some(u),
      emoji.shortnameToUnicode("cloud_with_lightning"))
    assertEquals(Some(uri),
      emoji.shortnameToImage("cloud_with_lightning"))
    assertEquals(None, emoji.shortnameToUnicode("--unknown--"))
    assertEquals(None, emoji.shortnameToImage("--unknown--"))
  }

  @Test def testFindEmojiCandidates: Unit = {
    assertEquals(Seq(), emoji.findEmojiCandidates("foo bar baz"))
    assertEquals(Seq((0, 4), (6, 10), (12, 16)), emoji.findEmojiCandidates(":foo: :bar: :baz:"))
    assertEquals(Seq((0, 4), (6, 10)), emoji.findEmojiCandidates(":foo: :bar::baz:"))
    assertEquals(Seq((0, 4), (6, 10)), emoji.findEmojiCandidates(":foo: :bar:baz:"))
    assertEquals(Seq((7, 11), (14, 21)), emoji.findEmojiCandidates("x:foo: :bar:x :a_1b_c:"))
    assertEquals(Seq(), emoji.findEmojiCandidates("foo :: bar"))
    assertEquals(Seq(), emoji.findEmojiCandidates("foo ::bar:"))
  }

  @Test def testReplaceEmojis: Unit = {
    import EmojiParserExtension._
    assertEquals(None, emoji.replaceEmojis("foo bar baz"))
    assertEquals(None, emoji.replaceEmojis(":unknown_emoji: foo bar"))
    assertEquals(Some(Seq(PlainText("foo "), EmojiImage("cloud_with_lightning", u, uri), PlainText(" bar"))),
      emoji.replaceEmojis("foo :cloud_with_lightning: bar"))
    assertEquals(Some(Seq(EmojiImage("cloud_with_lightning", u, uri), PlainText(" bar"))),
      emoji.replaceEmojis(":cloud_with_lightning: bar"))
    assertEquals(Some(Seq(PlainText("foo "), EmojiImage("cloud_with_lightning", u, uri))),
      emoji.replaceEmojis("foo :cloud_with_lightning:"))
    assertEquals(Some(Seq(PlainText("foo "), EmojiImage("cloud_with_lightning", u, uri), PlainText(" "), EmojiImage("cloud_with_lightning", u, uri), PlainText(" bar"))),
      emoji.replaceEmojis("foo :cloud_with_lightning: :cloud_with_lightning: bar"))
  }
}
