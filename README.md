wicket-safemodel
================
A typesafe and refactoring-safe way to build Wicket `PropertyModel`s.

**Current version: 1.3**

New in this release:

*   Can now work with `IModel`s as targets. Reflection and proxying is done on
    a best-effort basis.
    
*   In addition to `PropertyModel`s, SafeModel can now also build 
    `LoadableDetachableModel`s to wrap backend service calls. See below.


Why do I need this?
-------------------
There are (vastly simplified for the sake of the argument) two ways to build models
in Wicket. One is typesafe and refactoring-safe:

    SomeBean myBean = ...
    IModel<String> childNameModel = new Model<String>() {
        public void setObject(String s) {
            myBean.getChild().setName(s);
        }
        public String getObject() {
            return myBean.getChild().getName();
        }
    };
    
As you can see, this way is also utterly verbose.

The other way is much better in this regard:

    IModel<String> childNameModel = new PropertyModel<String>(
        myBean, "child.name");

A one-liner, basically. Of course, this will embarrassingly fail at runtime if
you refactor your bean and rename even just one of the properties along the
path to what your model is pointing to.
It will also fail at runtime if you have a typo in the path string. There is no
way to catch any of this at compile time.

Or is there?

What do we want instead?
------------------------
What we really want are functional models like in the Scala extensions for Wicket.
Unfortunately, most of us are stuck in Java world where proper functions are still
considered science fiction like flying cars and World Peace.

We still want something concise, typesafe, refactoring-safe and (mostly) compilesafe.
This can be achieved with something like [metagen](https://github.com/42Lines/metagen).
Unfortunately, this requires an additional build step since it uses bytecode instrumentation.
On the plus side, it gives you good compile-safety at almost no runtime cost. On the downside,
you have to get it working in your main (probably Maven) build, and most likely separately
in your IDE build settings. This can be awkward, especially for third-party developers.

If you feel comfortable with configuring annotation processors, use metagen, since it will
give you more than a reflection-based technique ever can.

If you don't want to fiddle with your build, yet still want more safety for almost
all use cases, read on.

Introducing SafeModel
---------------------
How about this?

    SomeBean myBean = ...
    IModel<String> childNameModel = model(from(myBean).getChild().getName());
    
Note that there is no casting, nor any type annotation on the right hand side. There is
no string literal either, yet everything is fully typesafe. Cool, huh?

How do I use it?
----------------
*   Add my maven repo to your pom.xml:

        <repositories>
          <repository>
            <id>duesenklipper</id>
            <url>http://duesenklipper.github.com/maven/releases</url>
            <snapshots>
              <enabled>false</enabled>
            </snapshots>
            <releases>
              <enabled>true</enabled>
            </releases>
          </repository>
        </repositories>
        
*   Add a dependency on safemodel1.4 if you use Wicket 1.4.x...

        <dependency>
          <groupId>de.wicketbuch.safemodel</groupId>
          <artifactId>safemodel1.4</artifactId>
          <version>1.3</version>
        </dependency>
        
    ...or on safemodel1.5 if you use Wicket 1.5.x:

        <dependency>
          <groupId>de.wicketbuch.safemodel</groupId>
          <artifactId>safemodel1.5</artifactId>
          <version>1.3</version>
        </dependency>

*   Add a static import to your class to get the `from` and `model` methods into your scope:

        import static de.wicketbuch.safemodel.SafeModel.*;
        
*   Then use it as shown above - use `from(my-root-object)` to start and walk through
    your getters to the property you need. Wrap the whole thing in `model(...)` and you get
    a properly typechecked `IModel` instance. You can use any `IModel<root-type>` as a root
    object. In this case, reflection and proxying will be more difficult, so if you have truly
    strange generics, this might fail. It will not creep up on you though - it either works the
    first time, or it doesn't.
    
*   To get a `LoadableDetachableModel` to wrap backend calls, use `fromService` instead of `from`:

        IModel<User> userModel = model(fromService(userEJB).loadUser(42));
        
    This will give you a `LoadableDetachableModel` that will load the user with the ID `42`.
    Note that in this case no arbitrary chaining of method calls is possible - just
    `fromService(<service>.<methodcall>)`. This should cover most use cases.

Currently this works only with non-final JavaBean-style objects with standard getter methods.
It also supports `java.util.List<T>`s and `java.util.Map<String, V>`s. Note that only
string keys are allowed for maps.

This does not cover *all* imaginable use cases, but for the 90% of cases where you have
simple JavaBean-style domain classes, this should be useful.
    
Enjoy!

Carl-Eric

----

### Can I use this? What's the license? ###
It's only locally-tested code, so take it with a grain of salt for now.

The license is Apache 2.0, so you can do almost anything you like.

### How does it work? ###
Under the hood, the `from` method uses jMock's `ClassImposteriser` to create a proxy around the
given bean. The following calls each record the property name they are at and return further
proxies. The `model` method captures the whole affair and constructs an old-fashioned property
path string from the recorded property names. It then creates a new `PropertyModel` using the
originally given bean and the recorded property path.

Types work out because the type parameter of the model will be the type of the whole expression
passed to `model` - and that is the type of the last getter in the chain, which is exactly what
we want. Thus we can safely use the normally unsafe PropertyModels, because the creation process
is stricter than just writing a string. Type errors and refactorings will be caught by this.

There may be runtime errors only if you have odd class hierarchies that end up confusing SafeModel.
With any "normal" JavaBean-style classes this should work just fine. See the source for SafeModelTest
for further examples.

### Acknowledgements ###
The idea for this was lifted wholesale from the [LambdaJ-based proposal on the Wicket wiki](https://cwiki.apache.org/WICKET/working-with-wicket-models.html#WorkingwithWicketmodels-LambdaJ).
Instead of pulling in LambdaJ as a dependency, SafeModel uses jMock's ClassImposteriser directly (both LambdaJ and Mockito use that as well). The code was also simplified to not
require the root object's class to be passed in.
