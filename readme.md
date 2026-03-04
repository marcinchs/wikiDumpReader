This is a simple library designed to help reading fandom.com wiki dump files. See attached schema to see dump file structure. The file itself can be downloaded from any
`https://<fandom name>.fandom.com/wiki/Special:Statistics` web page. Scroll down to `Database dumps` section.

The main method `getPages()` reads a file from the filesystem and returns a list of `PageType` objects which can be then divided into sections which in turn may be stripped of markings to (only maybe) get clean text. The main focus was on the very first section, most probably others won't be that great, unfortunately.
See Javadocs, maybe they're of some help.

Build works with jdk21 and maven 3.9.x:

`mvn clean install` 

will also build sources.

Then add a dependency to your project:

```xml
<dependency>
    <groupId>org.mcs.wiki</groupId>
    <artifactId>dump-reader</artifactId>
</dependency>
```