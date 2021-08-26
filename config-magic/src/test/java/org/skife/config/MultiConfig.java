package org.skife.config;

interface MultiConfig
{
    @Config("singleOption")
    @Default("failed!")
    String getSingleOption();

    @Config({"singleOption"})
    @Default("failed!")
    String getSingleOption2();

    @Config({"multiOption1", "multiOption2"})
    @Default("failed!")
    String getMultiOption1();

    @Config({"doesNotExistOption", "multiOption2"})
    @Default("failed!")
    String getMultiOption2();

    @Config({"doesNotExistOption", "alsoDoesNotExistOption"})
    @Default("theDefault")
    String getMultiDefault();

    @Config({"${key}ExtOption", "defaultOption"})
    @Default("failed!")
    String getReplaceOption();
}




