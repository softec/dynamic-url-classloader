package lu.softec.net;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLStreamHandlerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class CachedURLClassLoaderFactory
{
    private Collection<WeakReference<ComparableURLClassLoader>> cache;

    private Collection<WeakReference<ComparableURLClassLoader>> stoppedClassLoader;
    private ReferenceQueue<ComparableURLClassLoader> weakQueue;
    private int statNewLoader;
    private int statReusedLoader;
    private int statDroppedLoader;
    
    public CachedURLClassLoaderFactory() {
        this(10);
    }

    /**
     * @param capacity  Initial capacity
     */
    public CachedURLClassLoaderFactory(int capacity) {
        cache = new ArrayList<WeakReference<ComparableURLClassLoader>>(capacity);
        stoppedClassLoader = new ArrayList<WeakReference<ComparableURLClassLoader>>(capacity);
        weakQueue = new ReferenceQueue<ComparableURLClassLoader>();
    }

    /**
     * Retrieve an existing or create a new ComparableURLClassLoader for the specified URLs using the
     * default delegation parent <code>ClassLoader</code>. URLs will be handle using
     * a DynamicJarURLStreamHandler to control usage of caches by the underlying protocol.
     * 
     * @param urls the URLs from which to load classes and resources
     * @return an appropriate class loader for the provided arguments
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkCreateClassLoader</code> method doesn't allow 
     *             creation of a class loader or its <code>checkPermission</code> method 
     *             does not allow the ReflectPermission("suppressAccessChecks") permission.
     * @see lu.softec.net.ComparableURLClassLoader
     */
    public synchronized ComparableURLClassLoader getURLClassLoader(URL[] urls)
    {
        cleanUpCache();
        int hashCode = ComparableURLClassLoader.getHashCode(urls);
        ComparableURLClassLoader classLoader = getURLClassLoader(hashCode);
        if( classLoader == null ) {
            classLoader = new ComparableURLClassLoader(urls);
            statNewLoader++;
            assert(hashCode == classLoader.hashCode());
            addURLClassLoader(classLoader);
        } else {
            statReusedLoader++;
        }
        return classLoader;
    }
    
    /**
     * Retrieve an existing or create a new ComparableURLClassLoader for the specified URLs using the
     * specified delegation parent <code>ClassLoader</code>. URLs will be handle using
     * a DynamicJarURLStreamHandler to control usage of caches by the underlying protocol.
     * 
     * @param urls the URLs from which to load classes and resources
     * @param parent the parent class loader for delegation
     * @return an appropriate class loader for the provided arguments
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkCreateClassLoader</code> method doesn't allow 
     *             creation of a class loader or its <code>checkPermission</code> method 
     *             does not allow the ReflectPermission("suppressAccessChecks") permission.
     * @see lu.softec.net.ComparableURLClassLoader
     */
    public synchronized ComparableURLClassLoader getURLClassLoader(URL[] urls, ClassLoader parent)
    {
        cleanUpCache();
        int hashCode = ComparableURLClassLoader.getHashCode(urls,parent);
        ComparableURLClassLoader classLoader = getURLClassLoader(hashCode);
        if( classLoader == null ) {
            classLoader = new ComparableURLClassLoader(urls,parent);
            statNewLoader++;
            assert(hashCode == classLoader.hashCode());
            addURLClassLoader(classLoader);
        } else {
            statReusedLoader++;
        }
        return classLoader;
    }
    
    /**
     * Retrieve an existing or create a new ComparableURLClassLoader for the specified URLs using the specified delegation parent
     * <code>ClassLoader</code>.
     * 
     * @param urls the URLs from which to load classes and resources
     * @param parent the parent class loader for delegation
     * @param factory the URLStreamHandlerFactory to use when creating URLs
     * @return an appropriate class loader for the provided arguments
     * @exception SecurityException if a security manager exists and its <code>checkCreateClassLoader</code> method
     *                doesn't allow creation of a class loader or its <code>checkPermission</code> method does not allow
     *                the ReflectPermission("suppressAccessChecks") permission.
     * @see lu.softec.net.ComparableURLClassLoader
     */
    public synchronized ComparableURLClassLoader getURLClassLoader(URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory)
    {
        cleanUpCache();
        int hashCode = ComparableURLClassLoader.getHashCode(urls,parent,factory);
        ComparableURLClassLoader classLoader = getURLClassLoader(hashCode);
        if( classLoader == null ) {
            classLoader = new ComparableURLClassLoader(urls,parent,factory);
            statNewLoader++;
            assert(hashCode == classLoader.hashCode());
            addURLClassLoader(classLoader);
        } else {
            statReusedLoader++;
        }
        return classLoader;
    }
    
    /**
     * @return the number of create loader by this factory
     */
    public int getStatNewLoader()
    {
        return statNewLoader;
    }

    /**
     * @return the number of successful reuse of an existing loader
     */
    public int getStatReusedLoader()
    {
        return statReusedLoader;
    }

    /**
     * @return the number of loader that have been garbage collected
     */
    public int getStatDroppedLoader()
    {
        return statDroppedLoader;
    }

    
    /**
     * Store a ComparableURLClassLoader in the cache
     * 
     * @param classLoader a ComparableURLClassLoader to be added
     */
    private void addURLClassLoader( ComparableURLClassLoader classLoader ) {
        cache.add(new WeakReference<ComparableURLClassLoader>(classLoader,weakQueue));
    }
    
    /**
     * Retrieve an existing ComparableURLClassLoader having the specified hashCode in the cache if available.
     * 
     * @param hashCode hash value of the class loader to retrieve
     * @return an existing ComparableURLClassLoader, or null if none are available.
     * @see lu.softec.net.ComparableURLClassLoader
     */
    private ComparableURLClassLoader getURLClassLoader( int hashCode ) {
        Iterator<WeakReference<ComparableURLClassLoader>> it = cache.iterator();
        while (it.hasNext()) {
            WeakReference<ComparableURLClassLoader> weakRef = it.next();
            ComparableURLClassLoader classLoader = weakRef.get();
            if( classLoader != null && classLoader.hashCode() == hashCode ) {
                if (classLoader.isVolatile() && classLoader.isOutdated()) {
                    it.remove();
                    stoppedClassLoader.add(weakRef);
                    if (!classLoader.stop())
                        refreshCache();
                    return null;
                } else {
                    return classLoader;
                }
            } 
        }
        return null;
    }

    /**
     * Remove outdated class loaders from the cache.
     */
    public synchronized boolean refreshCache()
    {
        boolean result = false;
        Iterator<WeakReference<ComparableURLClassLoader>> it = cache.iterator();
        while (it.hasNext()) {
            WeakReference<ComparableURLClassLoader> weakRef = it.next();
            ComparableURLClassLoader classLoader = weakRef.get();
            if (classLoader != null && classLoader.isOutdated()) {
                it.remove();
                stoppedClassLoader.add(weakRef);
                classLoader.stop();
                result = true;
            }
        }
        return result;
    }

    /**
     * Remove reference to garbage collected class loader from the cache.
     */
    private void cleanUpCache() {
        Reference<? extends ComparableURLClassLoader> weakRef;
        while( (weakRef = weakQueue.poll()) != null ) {
            if (!stoppedClassLoader.remove(weakRef))
                cache.remove(weakRef);
            statDroppedLoader++;
        }
    }
}
