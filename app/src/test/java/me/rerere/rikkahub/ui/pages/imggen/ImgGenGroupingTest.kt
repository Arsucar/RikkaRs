package me.rerere.rikkahub.ui.pages.imggen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImgGenGroupingTest {
    @Test
    fun groupsPromptWithInsertedVariant() {
        val basePrompt = """
            远景，校园的沥青马路上，左侧是圆形花坛包裹的青草地，右侧是食堂大楼。
            图片中间是沥青马路，侧面视角，前面走过一个女孩，她的身材很好，
            穿着黑色运动热裤裤脚和封边有白色条纹
        """.trimIndent().replace("\n", "")
        val insertedPrompt = """
            远景，校园的沥青马路上，左侧是圆形花坛包裹的青草地，右侧是食堂大楼。
            图片中间是沥青马路，侧面视角，前面走过一个金发挑染粉色头发，
            左手提着大包的零食购物袋。女孩，她的身材很好，穿着黑色运动热裤裤脚和封边有白色条纹
        """.trimIndent().replace("\n", "")

        val groups = buildGeneratedImageGroups(
            listOf(
                image(id = 1, prompt = basePrompt, timestamp = 1L),
                image(id = 2, prompt = insertedPrompt, timestamp = 2L),
            )
        )

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().variants.size)
        assertTrue(groups.single().variants.any { it.label == "原始版本" })
        assertTrue(groups.single().variants.any { it.label.contains("金发挑染粉色头发") })
    }

    @Test
    fun groupsPromptWithReplacedVariant() {
        val eatingPrompt = "远景，校园马路，前面是我喜欢的女孩，她有着一头天然的金发，有神的蓝色眼睛，在吃饭"
        val runningPrompt = "远景，校园马路，前面是我喜欢的女孩，她有着一头天然的金发，有神的蓝色眼睛，在跑步"

        val groups = buildGeneratedImageGroups(
            listOf(
                image(id = 1, prompt = eatingPrompt, timestamp = 1L),
                image(id = 2, prompt = runningPrompt, timestamp = 2L),
            )
        )

        assertEquals(1, groups.size)
        assertEquals(listOf("跑步", "吃饭"), groups.single().variants.map { it.label })
    }

    @Test
    fun groupsInsertedAndRewrittenPromptsInSameScene() {
        val basePrompt = """
            远景，校园的沥青马路上，左侧是圆形花坛包裹的青草地，右侧是食堂大楼。
            图片中间是沥青马路，侧面视角，前面走过一个女孩，她的身材很好，
            穿着黑色运动热裤裤脚和封边有白色条纹，头上戴着帽子，披肩散发，
            她的小腿和大腿线条很明显，但不是举重的那种粗壮，而是笔直修长微微有力的肌肉，
            有点像是经常运动的人甚至有运动员那样的效果，上衣是白色外套那种轻飘飘的，她。
            我没有看清具体正脸，只有侧脸，但感觉是非常漂亮好看的女孩，但是她给了我难忘的感觉，
            我想通过手机3倍聚焦放大效果，弥补我的遗憾，并且需要虚化背景
        """.trimIndent().replace("\n", "")
        val insertedPrompt = """
            远景，校园的沥青马路上，左侧是圆形花坛包裹的青草地，右侧是食堂大楼。
            图片中间是沥青马路，侧面视角，前面走过一个金发挑染粉色头发，
            左手提着大包的零食购物袋。女孩，她的身材很好，穿着黑色运动热裤裤脚和封边有白色条纹，
            头上戴着帽子，披肩散发，她的小腿和大腿线条很明显，但不是举重的那种粗壮，
            而是笔直修长微微有力的肌肉，有点像是经常运动的人甚至有运动员那样的效果，
            上衣是白色外套那种轻飘飘的，她。我没有看清具体正脸，只有侧脸，
            但感觉是非常漂亮好看的女孩，但是她给了我难忘的感觉，我想通过手机3倍聚焦放大效果，
            弥补我的遗憾，并且需要虚化背景
        """.trimIndent().replace("\n", "")
        val rewrittenPrompt = """
            远景，校园的沥青马路上，左侧是圆形花坛包裹的青草地，右侧是食堂大楼。
            图片中间是沥青马路，侧面视角，前面走过一个女孩，她的身材很好，
            穿着黑色运动热裤和短袖，黑色短袖，头上戴着帽子，披肩散发，
            她的小腿和大腿线条很明显，像是经常运动的人甚至有运动员那样的效果。
            我没有看清具体正脸，但感觉是很好看的女孩，但是她给了我难忘的感觉，
            我想通过手机4倍聚焦放大效果，弥补我的遗憾，并且需要虚化背景
        """.trimIndent().replace("\n", "")

        val groups = buildGeneratedImageGroups(
            listOf(
                image(id = 1, prompt = basePrompt, timestamp = 1L),
                image(id = 2, prompt = insertedPrompt, timestamp = 2L),
                image(id = 3, prompt = rewrittenPrompt, timestamp = 3L),
            )
        )

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().variants.size)
        assertTrue(groups.single().variants.any { it.label.contains("金发挑染粉色头发") })
        assertTrue(groups.single().variants.any { it.label.contains("4") })
    }

    private fun image(id: Int, prompt: String, timestamp: Long): GeneratedImage {
        return GeneratedImage(
            id = id,
            prompt = prompt,
            filePath = "image-$id.png",
            timestamp = timestamp,
            model = "gpt-image-2",
        )
    }
}
