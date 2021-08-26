# Example

Create an interface for your config object:

    public interface MyConfig
    {
        @Config("foo")
        String getFoo();

        @Config("blah")
        int getBlah();

        @Config("what")
        @Default("none")
        String getWhat();
    }

Set the properties that we mapped with `@Config` above (or simply call `System.getProperties()`):

    Properties props = new Properties();
    props.setProperty("foo", "hello");
    props.setProperty("blah", "123");

Then create the config object from the properties:

    ConfigurationObjectFactory factory = new ConfigurationObjectFactory(props);
    MyConfig conf = factory.build(MyConfig.class);

# Default values

Using `@Default()` can set arbitrary default values. To set `null` as the default value, use the `@DefaultNull`annotation.

# Advanced usage

        @Config({"what1", "what2"})
        @Default("none")
        String getWhat();

will look at `what1` first, then at `what2` and finally fall back to the default.

# Type support

Config-magic supports these types:

* Primitive types: `boolean`, `byte`, `short`, `integer`, `long`, `float`, `double`.
* Enums. Note that config-magic by default ignores the case for enum values.
* `java.lang.String`.
* `java.net.URI`.
* `java.lang.Class` and simple wildcard extensions (`java.lang.Class<?>`, `java.lang.Class<? extends Foo>` - config-magic will type check that the type passed as a property conforms to the wildcard type), but not more complex wildcard or parameterized types (e.g. `java.lang.Class<? super Bar>` or `java.lang.Class<? extends List<? super Bar>>`).
* `org.skipe.config.TimeSpan`: constructed from short textual representation like "5d" (or alias "5 days"); units supported are:
 * ms (alias 'milliseconds')
 * s ('seconds')
 * m ('minutes')
 * h ('hours')
 * d ('days')
* Any instantiable class that has a public constructor with a single `Object` parameter. This is useful for instance for [joda-time](http://joda-time.sourceforge.net/)'s `DateTime` objects.
* Any instantiable class that has a public constructor with a single `String` parameter. This is useful for instance for `java.lang.File`.
* Any class that has a static `valueOf` method with a single `String` parameter and the class as its return type.

# Maven dependency

To use config-magic in Maven projects:

    <dependency>
        <groupId>org.skife.config</groupId>
        <artifactId>config-magic</artifactId>
        <version>0.11</version>
    </dependency>

# Mailing List

We have a [mailing list](http://groups.google.com/group/config-magic) for development and users.
