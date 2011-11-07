Dynamic URL Class Loader
========================

#### Java Class loading and reloading from URLs ####

This project aims to facilitate Java classes reloading at run-time. It provides
a custom URL class loader that allow proper control over the JAR cache and
a class loader factory that provided such class loader, and cache them
to limit the multiplication of similar classloader.

### Targeted platforms ###

This implementation is dependant on the Sun JVM. This has been done to limitate,
as much as possible, the coding required by this implementation and benefit of
the existing URL, JAR and class handling provided in the Sun JVM implementation.
If you do not use the Sun JVM, you are out of luck, this project will not
helps you. Moreover, if you are using a security manager, this code require the
ReflectPermission("suppressAccessChecks") permission to access a private static
function of the Sun JVM.

This code has been heavily test under the Sun JVM 1.6

Contributing to this project
----------------------------

Fork our repository on GitHub and submit your pull request.

Documentation
-------------

There is no documentation for this project but the code is documented using JavaDoc.


License
-------

This code is copyrighted by [SOFTEC sa](http://softec.lu) and is licenced under
the terms of the [GNU Lesser General Public License](http://www.gnu.org/licenses/lgpl-2.1.html)
as published by the Free Software Foundation; either version 2.1 of the License, or
(at your option) any later version.
