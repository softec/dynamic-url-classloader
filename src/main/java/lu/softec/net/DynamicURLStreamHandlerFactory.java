/**
 * Copyright (C) 2009 SOFTEC sa.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA 
 */
package lu.softec.net;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * This is an implementation of a factory for <code>URL</code> stream protocol handlers that allow fine control over the
 * caching and closing of jar files. It is used by the DynamicURLClassLoader.
 * 
 * @author Denis Gervalle (support@softec.lu)
 * @see java.net.URL
 * @see lu.softec.net.DynamicJarURLStreamHandler
 * @see lu.softec.net.DynamicURLClassLoader
 */
class DynamicURLStreamHandlerFactory implements URLStreamHandlerFactory
{
    private URLStreamHandlerFactory factory;
    private Method getURLStreamHandler = null;

    private WeakReference<DynamicURLClassLoader> classLoader = null;

    /**
     * Store the jarHandler of the DynamicClassLoader that had created this factory
     */
    protected DynamicJarURLStreamHandler jarHandler = null;

    /**
     * Constructs a new <code>DynamicJarURLStreamHandlerFactory</code> that wrap an existing
     * <code>URLStreamHandlerFactory</code> to wrap created <code>URLStreamHandler</code> with a
     * <code>DynamicJarURLStreamHandler</code> to controls jar caching and closing.
     * 
     * @param factory the URLStreamHandlerFactory to use when creating URLs
     * @see java.net.URLStreamHandlerFactory
     * @see lu.softec.net.DynamicJarURLStreamHandler
     */
    public DynamicURLStreamHandlerFactory(URLStreamHandlerFactory factory)
    {
        this.factory = factory;
        if( factory == null ) {
            try {
                this.getURLStreamHandler = URL.class.getDeclaredMethod("getURLStreamHandler", new Class[] {String.class});
                this.getURLStreamHandler.setAccessible(true);
            } catch (NoSuchMethodException e) { }
        }
    }

    /**
     * Constructs a new <code>DynamicJarURLStreamHandlerFactory</code> that wrap default <code>URLStreamHandler</code>
     * with a <code>DynamicJarURLStreamHandler</code> to controls jar caching and closing.
     * 
     * @exception SecurityException if a security manager exists and its <code>checkPermission</code> method does not
     *                allow the ReflectPermission("suppressAccessChecks") permission.
     * @see lu.softec.net.DynamicJarURLStreamHandler
     */
    public DynamicURLStreamHandlerFactory() {
        this(null);
    }

    /**
     * Creates a new <code>URLStreamHandler</code> instance with the specified protocol. This handler either came from
     * the cache of previously created stream handler or is retrieve using <code>getURLStreamHandler</code>. The default
     * implementation of <code>getURLStreamHandler(String)</code> is made for the Sun Java JRE and either retrieve the
     * URLStreamHandler by calling the package private method <code>URL.getURLStreamHandler(String)</code> or the
     * handler factory provided. If you are not using Sun Java, you should probably supply a replacement for
     * <code>getURLStreamHandler(String)</code> method or always use a handler factory when the factory is constructed.
     * When the protocol is <code>jar</code>, this factory will embed the retrieved stream handler into a
     * <code>DynamicJarURLStreamHandler</code> to be able to controls jar caching and closing.
     * 
     * @param protocol the protocol ("<code>ftp</code>", "<code>http</code>", "<code>nntp</code>", etc.).
     * @return a <code>DynamicJarURLStreamHandler</code> for the specific protocol.
     * @see lu.softec.net.DynamicJarURLStreamHandler
     */
    public URLStreamHandler createURLStreamHandler(String protocol)
    {
        if (protocol.equals("jar") && jarHandler != null ) {
            return jarHandler;
        }
        
        URLStreamHandler handler = getURLStreamHandler(protocol);
        if (handler != null && protocol.equals("jar")) {
            synchronized (this) {
                if (jarHandler != null)
                    return jarHandler;
                jarHandler = new DynamicJarURLStreamHandler(handler);
                handler = jarHandler;
                if (classLoader != null)
                    jarHandler.setClassLoader(classLoader.get());
            }
        }

        return handler;
    }

    /**
     * Try to retrieve a stream handler for the given protocol by calling the handler factory provided to this one or
     * the package private method <code>URL.getURLStreamHandler(String)</code> (Sun Java). If none of the above succeed,
     * return null. If you are not using Sun Java, you should probably supply a replacement for this method or always
     * use a handler factory when the factory is constructed.
     * 
     * @param protocol the protocol ("<code>ftp</code>", "<code>http</code>", "<code>nntp</code>", etc.).
     * @return a <code>URLStreamHandler</code> for the specific protocol.
     */
    protected URLStreamHandler getURLStreamHandler(String protocol)
    {
        URLStreamHandler handler = null;

        if (factory != null)
            handler = factory.createURLStreamHandler(protocol);

        if (handler == null && this.getURLStreamHandler != null) {
            try {
                handler = (URLStreamHandler) this.getURLStreamHandler.invoke(URL.class, protocol);
            } catch (IllegalArgumentException e) {

            } catch (IllegalAccessException e) {

            } catch (InvocationTargetException e) {

            }
        }
        return handler;
    }

    /**
     * Define the DynamicClassLoader that have created this instance
     * 
     * @param classLoader the class loader using this factory
     */
    public void setClassLoader(DynamicURLClassLoader classLoader)
    {
        if( classLoader != null ) {
            this.classLoader = new WeakReference<DynamicURLClassLoader>(classLoader);
            if (jarHandler != null)
                jarHandler.setClassLoader(classLoader);
        }
    }
}
