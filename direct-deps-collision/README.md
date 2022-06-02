# Version convergence of direct extension dependencies

This project demonstrates how the ordering of dependencies in the application's `pom.xml` affect the classpath of the application.

In this case, we have:

* `extension-red` depending on `antlr:antlr:2.7.2`;
* `extension-blue` depending on `antlr:antlr:2.7.7`;
* `platform-bom` managing versions of the extensions above but **not** their dependencies (as many would consider a recommended practice).

Build the project with `./mvnw clean package` from the root project dir.

Now list the application classpath with `ls app/target/quarkus-app/lib/main/` from the root project dir.

You'll see `antlr.antlr-2.7.2.jar` among the JARs.

Now open `app/pom.xml` in an editor and change the ordering of extensions by moving `extension-blue` above `extension-red`, re-build the application and list its classpath.
You'll see `antlr.antlr-2.7.7` among the JARs.

This means that:

* if an application depends only on `extension-red` and not `extension-blue`, it will end up with Antlr 2.7.2 on its classpath;
* if an application depends only on `extension-blue` and not `extension-red`, it will end up with Antlr 2.7.7 on its classpath;
* if an application depends on both `extension-red` and `extension-blue`, the version of Antlr ending up on its classpath will depend on the order in which extensions appear in the `pom.xml`.

**IMPORTANT:** Antlr is a **simple** artifact that has no dependencies on its own. If it did have its own transitive dependencies, the effects on the application's classpath
would have been more dramatic. By changing the ordering of extensions in the POM, not only the version of Antlr itself but the whole set Antlr's dependencies would be replaced with it!

This simple example shows how **seemingly** insignificant changes (like changing the ordering of extensions in the POM) may significantly change the runtime classpath of the application.

From the Quarkus team perspective, taking into account that an application may depend on many extensions and other libraries, what transitive dependencies will end up on the classpath of an application is,
generally, unpredicatable until we get hold of a specific `pom.xml`.

## Controlling transitive dependencies

There is an easy way to take control of the Antlr version in this case though by adding it to the `platform-bom`.

Open `platform-bom/pom.xml` with an editor and add

````
      <dependency>
        <groupId>antlr</groupId>
        <artifactId>antlr</artifactId>
        <version>2.7.5</version>
      </dependency>
````

to its `dependencyManagement` section.

Re-build the application and list its classpath. Now no matter what the ordering of the extensions is in the application's `pom.xml`, the version of Antlr will always be 2.7.5 (unless the application explicitly overrides it
or some other BOM containing a different version of Antlr is imported before our `platform-bom`).

Adding critical thirdparty dependencies to the BOM is a way to resolve dependency conflicts and have a more predictable classpath. Although many consider including thirdparty dependencies in the BOM a bad practice
because it may complicate integrating libraries and extensions that aren't managed by platform BOM in the application.

Let's consider the following example. Imagine we also have `extension-green` that **does not** depend on Antlr at all. And there is some thirdparty library that depends on something that requires Antlr version 2.1.1.
If our `platform-bom` includes an Antlr version other than 2.1.1, it will be troublesome to integrate that thirdparty library in application using `extension-green` that does not care about Antlr version. True,
the application in this case could **override** the version of Antlr configured in the `platform-bom`. However, if we consider an extreme case of adding all/most of the transitive dependencies of Quarkus extensions
to the `platform-bom` it may lead to a very bad user experience figuering out, basically, how to "reset" version constraints in the `platform-bom` for thirdparty dependencies.

# Conclusion

## BOM composition

Managing a limited number of dependencies in the BOM may result in a very unpredictable application classpath that will depend on seemingly insignificant conditions (like the order of platform extensions).

Managing too many dependencies in the BOM may lead to a very bad user experience resolving version conflicts with third party libraries and extensions.

It looks like there should be some kind of balance between managed and unmanaged dependencies in the platform BOMs that should be making it easy for most common use-cases and require adjustemnts in the application in more rare ones.

## Dynamic nature of the application classpath

Given that we are not including all the extension dependencies in the platform BOM, we have to accept the fact that at least **some part** of the classpath of applications using platform extensions may be hard to predict and will depend on ordering of the extension dependencies and other libraries in the application's `pom.xml`.

## Productisation of non-managed dependencies

Given that not all the dependencies of extensions will end up in the platform BOM, there is a question whether they should still be productized. Here is why it probably **does not** make sense.

If a dependency isn't managed in the BOM, we can't 100% be sure about which version of it will end up on the classpath. We may track down a few based on our dependency analysis. But then would we support a few different versions of the same dependency at the same time? Let's also take into account that each version of the dependency may have a very different transitive dependency tree. Would we productize all those transitive dependency trees? It doesn't seem to be a worthy investment of time and effort.

It looks like we have to accept the fact that as long as there are non-managed extension dependencies, we will have non-productized dependencies on the classpath.
