package org.mcs.wiki

import jakarta.xml.bind.JAXBContext
import org.mcs.wiki.model.Mediawiki
import org.mcs.wiki.model.PageType
import java.nio.file.Files
import java.nio.file.Paths


class WikiDumpReader {

    private val PAGE_TEXT_SECTION_DELIMITER_PATTERN = Regex("^==[A-Z|\\s].*")

    /**
     * Reads a file and returns a list of the pages
     * filtered by the most relevant ones, meaning
     * not including any special pages
     *
     * @param dumpFilePath absolute path to a wiki dump file
     * @param relevantOnly if true output will not contain any
     * pages of a special type (for example Category, User or Template);
     * if false output will contain
     * all the pages without any filtering
     * @return a list of the pages
     */
    fun getPages(dumpFilePath: String, relevantOnly: Boolean): List<PageType> {
        if (relevantOnly) {
            return getPages(dumpFilePath)
                .asSequence()
                .filterNot { pageType ->
                    pageType.title.contains(":") ||
                            pageType.revision.text.value.startsWith("#REDIRECT", ignoreCase = true)
                }.toList()
        }
        return getPages(dumpFilePath)
    }

    /**
     * Reads a file and returns a list of all the pages
     *
     * @param dumpFilePath path to wiki dump file
     * @return all the pages found in the dump file or an empty list
     */
    fun getPages(dumpFilePath: String): List<PageType> =
        try {
            Files.newInputStream(Paths.get(dumpFilePath))
                .reader(Charsets.UTF_8)
                .use { inputStreamReader ->
                    val mediawiki = jaxbContext().createUnmarshaller().unmarshal(inputStreamReader) as Mediawiki
                    mediawiki.page
                }.orEmpty()
        } catch (ex: Exception) {
            emptyList()
        }


    private fun jaxbContext(): JAXBContext {
        return JAXBContext.newInstance(Mediawiki::class.java)
    }

    /**
     * Reads a text from a page and divides it into main sections
     * By default will use '==' as section separator.
     *
     * @param page
     * @param delimiter - optional regex for section separator
     * @return list of sections
     */
    fun getTextSections(page: PageType, delimiter: Regex = PAGE_TEXT_SECTION_DELIMITER_PATTERN): List<List<String>> {
        val textValueList = page.revision.text.value
            .lineSequence()
            .toList()

        val bigArray = mutableListOf<List<String>>()
        var smallArray = mutableListOf<String>()

        for (line in textValueList) {
            if (line.matches(delimiter)) {
                bigArray.add(smallArray)
                smallArray = mutableListOf()
            }
            smallArray.add(line)
        }
        bigArray.add(smallArray)
        return bigArray
    }

    /**
     * Reads a text and finds start and end indexes of regions
     * using provided characters as separators.
     *
     * Example `findAllMatchingRegions("{sample{text}}", "{", "}")`
     * will return `[(7,12), (0,13)]`
     *
     * @param paragraph text
     * @param openingChar character used as region beginning
     * @param closingChar character used as region ending
     * @return list of pairs
     * @throws Exception if the number of provided opening and closing
     * character does not match.
     *
     */
    fun findAllMatchingRegions(paragraph: String, openingChar: String, closingChar: String): List<Pair<Int, Int>> {
        if (!isValid(paragraph, openingChar, closingChar)) {
            throw Exception("Regions are not valid: opening and closing don't match")
        }

        val openIndexes = mutableListOf<Int>()
        val closeIndexes = mutableListOf<Int>()

        paragraph.toCharArray()
            .withIndex()
            .forEach { (index, character) ->
                if (character.toString() == openingChar) {
                    openIndexes.add(index)
                }
                if (character.toString() == closingChar) {
                    closeIndexes.add(index)
                }
            }

        return openIndexes.reversed().mapNotNull { openNumber ->
            val found = findClosestClosingIndex(openNumber, closeIndexes)
            if (found != null) {
                closeIndexes.remove(found.second)
            }
            found
        }
    }

    private fun findClosestClosingIndex(openNumber: Int, closeIndexes: MutableList<Int>): Pair<Int, Int>? {
        var num = openNumber
        while (closeIndexes.indexOf(num) == -1) {
            num++
            if (num > closeIndexes.last()) {
                return null
            }
        }
        return Pair(openNumber, num)
    }

    /**
     * checks if paragraph has matching number of
     * opening and closing characters
     */
    private fun isValid(paragraph: String, openingChar: String, closingChar: String): Boolean {
        val openingCount = paragraph.count { it.toString() == openingChar }
        val endingCount = paragraph.count { it.toString() == closingChar }
        return openingCount == endingCount
    }

    /**
     * Reads a text and finds start and end indexes of regions
     * using provided characters as separators.
     * Only the most broad ranges are returned.
     *
     * Example `findMatchingRegions("{sample{text}}", "{", "}")`
     * will return `[(0,13)]`
     *
     * @param paragraph text
     * @param openingChar character used as region beginning
     * @param closingChar character used as region ending
     * @return list of pairs
     * @throws Exception if the number of provided opening and closing
     * character does not match.
     */
    fun findMatchingRegions(paragraph: String, openingChar: String, closingChar: String): List<Pair<Int, Int>> {
        val allRegions = findAllMatchingRegions(paragraph, openingChar, closingChar)
        return compactRanges(allRegions)
    }

    /**
     * Compacts the provided list to return
     * the most broad ranges.
     *
     * Example
     * `compactRanges(listOf(Pair(42, 56), Pair(45,52),
     *                 Pair(41, 57), Pair(1, 37),
     *                 Pair(0, 38)))`
     * will return `[(0,38), (41, 57)]`
     *
     * @param fullList list of pairs
     * @return list of pairs
     */
    fun compactRanges(fullList: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()

        fullList.sortedByDescending {
            it.first
        }.reversed().forEach { element ->
            if (list.isEmpty()) {
                list.add(element)
            }

            val lastAddedPair = list.last()
            if (element.first > lastAddedPair.second) {
                list.add(element)
            }
        }
        return list
    }

    /**
     * Removes provides ranges of characters from the string
     *
     * Example
     * `removeRegions("abcdefghijklmnopq", listOf(Pair(2, 5), Pair(12,15)))`
     * will return `abghijklq`
     *
     * @param paragraph string
     * @param regionBoundaries list of pairs of start - end indices
     * @return a string without characters in the ranges provided
     */
    fun removeRegions(
        paragraph: String,
        regionBoundaries: List<Pair<Int, Int>>
    ): String {
        val chars: MutableList<String?> = paragraph.toCharArray().map { it.toString() }.toMutableList()
        regionBoundaries.forEach { pair ->
            for (i in pair.first..pair.second) {
                chars[i] = null
            }
        }
        return chars.mapNotNull {
            it
        }.joinToString("")
    }

    /**
     * Removes wiki links like [[File:some text]] from the string
     *
     * @param paragraph string
     * @param opening string
     * @param closing string
     * @return a string without substring between provided `opening` and `closing`
     */
    fun removeSpecialLinks(paragraph: String, opening: String, closing: String): String {
        val regionBoundaries =
            findMatchingRegions(paragraph, opening.first().toString(), closing.last().toString())
        val outList = mutableListOf<Pair<Int, Int>>()
        regionBoundaries.forEach { pair ->
            val substring = paragraph.substring(pair.first, pair.second + 1)
            if (substring.startsWith(opening) && substring.endsWith(closing)) {
                outList.add(pair)
            }
        }
        return removeRegions(paragraph, outList)
    }
}
