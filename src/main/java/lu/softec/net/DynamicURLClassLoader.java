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

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class loader is a wrapper around the classical <code>URLClassLoader</code> that permits to control the usage of
 * the cache settings of URLConnection during class loading. Called the same way as an URLCacheLoader, it defaults to
 * disabling cache usage when loading classes from URLs.
 * 
 * @author Denis Gervalle (support@softec.lu)
 * @see java.net.URLClassLoader
 */
public class DynamicURLClassLoader extends URLClassLoader
{
    DynamicURLStreamHandlerFactory factory = null;

    private Collection<DynamicJarURLConnection> connections = new ConcurrentLinkedQueue<DynamicJarURLConnection>();

    private boolean stopped = false;

    DynamicURLClassLoader(URL[] urls, ClassLoader parent, DynamicURLStreamHandlerFactory factory)
    {
        super(urls, parent, factory);
        factory.setClassLoader(this);
    }

    /**
     * Constructs a new DynamicURLClassLoader for the specified URLs using the default delegation parent
     * <code>ClassLoader</code>. URLs will be handle using a DynamicJarURLStreamHandler that disable usage of caches by
     * the underlying protocol.
     * 
     * @param urls the URLs from which to load classes and resources
     * @exception SecurityException if a security manager exists and its <code>checkCreateClassLoader</code> method
     *                doesn't allow creation of a class loader or its <code>checkPermission</code> method does not allow
     *                the ReflectPermission("suppressAccessChecks") permission.
     * @see java.net.URLClassLoader
     * @see lu.softec.net.DynamicJarURLStreamHandler
     */
    public DynamicURLClassLoader(URL[] urls)
    {
        this(urls, getSystemClassLoader(), new DynamicURLStreamHandlerFactory());
    }

    /**
     * Constructs a new DynamicURLClassLoader for the specified URLs using the specified delegation parent
     * <code>ClassLoader</code>. URLs will be handle using a DynamicJarURLStreamHandler that disable usage of caches by
     * the underlying protocol.
     * 
     * @param urls the URLs from which to load classes and resources
     * @param parent the parent class loader for delegation
     * @exception SecurityException if a security manager exists and its <code>checkCreateClassLoader</code> method
     *                doesn't allow creation of a class loader or its <code>checkPermission</code> method does not allow
     *                the ReflectPermission("suppressAccessChecks") permission.
     * @see java.net.URLClassLoader
     * @see lu.softec.net.DynamicJarURLStreamHandler
     */
    public DynamicURLClassLoader(URL[] urls, ClassLoader parent)
    {
        this(urls, parent, new DynamicURLStreamHandlerFactory());
    }

    /**
     * Constructs a new DynamicURLClassLoader for the specified URLs using the specified delegation parent
     * <code>ClassLoader</code>. URLs will be handle using a DynamicJarURLStreamHandler to control usage of caches by
     * the underlying protocol by wrapping the handler provided by the specified URLStreamHandlerFactory.
     * 
     * @param urls the URLs from which to load classes and resources
     * @param parent the parent class loader for delegation
     * @param factory the URLStreamHandlerFactory to use when creating URLs
     * @param useCaches If true, the protocol that is used to loads bytecodes is allowed to use caching whenever it can.
     *            If false, it must always try to get a fresh copy of the bytecodes.
     * @exception SecurityException if a security manager exists and its <code>checkCreateClassLoader</code> method
     *                doesn't allow creation of a class loader or its <code>checkPermission</code> method does not allow
     *                the ReflectPermission("suppressAccessChecks") permission.
     * @see java.net.URLClassLoader
     * @see lu.softec.net.DynamicJarURLStreamHandler
     */
    public DynamicURLClassLoader(URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory)
    {
        this(urls, parent, new DynamicURLStreamHandlerFactory(factory));
    }

    /**
     * @param uc an URL connection currently in-use by this class loader
     */
    public void register(DynamicJarURLConnection uc)
    {
        connections.add((DynamicJarURLConnection) uc);
    }

    /**
     * @param uc an URL connection no more in-use by this class loader
     */
    public void clear(DynamicJarURLConnection uc)
    {
        connections.remove(uc);
    }

    /**
     * Check the jar file currently cached by this class loader against their remote version and return true when the
     * cached version of any of them is older than the remote one. For file retrieve through http connection, only a
     * head connection is made, and the server is expected to provide an appropriate last-modified header.
     * 
     * @return true if any jar file already opened (and cached) by this class loader is older than its remote version.
     */
    public boolean isOutdated()
    {
        boolean result = false;
        for (DynamicJarURLConnection uc : connections) {
            if (uc != null) {
                if (uc.isOutdated()) {
                    result = true;
                }
            }
        }
        return result;
    }

    /**
     * Stop this class loader and try to close its related JarFiles
     * 
     * @return true if all JarFiles associated to the JarURLConnection of this class loader has been closed
     *         successfully. If the class loader was already stopped, this function does not had any effect on Jar File
     *         closing but return an actuated result.
     */
    public boolean stop()
    {
        stopped = true;
        boolean result = true;
        Iterator<DynamicJarURLConnection> it = connections.iterator();
        while (it.hasNext()) {
            DynamicJarURLConnection uc = it.next();
            if (uc != null) {
                try {
                    if (uc.close()) {
                        it.remove();
                    } else {
                        result = false;
                    }
                } catch (IOException e) {
                }
            }
        }
        return result;
    }

    /**
     * @return true if this class loader has already been stopped
     */
    public boolean isStopped()
    {
        return stopped;
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable
    {
        stop();
        super.finalize();
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLClassLoader#findClass(java.lang.String)
     */
    @Override
    protected Class< ? > findClass(String name) throws ClassNotFoundException
    {
        if (stopped) {
            throw new ClassNotFoundException("ClassLoader is stopped");
        }
        return super.findClass(name);
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLClassLoader#findResource(java.lang.String)
     */
    @Override
    public URL findResource(String name)
    {
        if (stopped) {
            return null;
        }
        return super.findResource(name);
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLClassLoader#findResources(java.lang.String)
     */
    @Override
    public Enumeration<URL> findResources(String name) throws IOException
    {
        if (stopped) {
            return null;
        }
        return super.findResources(name);
    }
}
