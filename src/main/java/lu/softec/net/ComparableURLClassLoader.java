/**
 * Copyright (C) 2008 SOFTEC sa.
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

import java.net.URL;
import java.net.URLStreamHandlerFactory;

/**
 * This class loader extends URLClassLoader to allow easy identification. It
 * overwrite hashCode to provide a meaning full hash value that represent the
 * current class loader in regards to its arguments Urls, parent and Stream factory.
 */
public class ComparableURLClassLoader extends DynamicURLClassLoader
{
    private long URLHashCode = 0;

    private int FactoryHashCode = 0;

    private boolean isVolatile = true;

    /**
     * Constructs a new ComparableURLClassLoader for the specified URLs using the default delegation parent
     * <code>ClassLoader</code>.
     * 
     * @param urls the URLs from which to load classes and resources
     * @exception SecurityException if a security manager exists and its <code>checkCreateClassLoader</code> method
     *                doesn't allow creation of a class loader or its <code>checkPermission</code> method does not allow
     *                the ReflectPermission("suppressAccessChecks") permission.
     * @see java.net.URLClassLoader
     */
    public ComparableURLClassLoader(URL[] urls)
    {
        super(urls);
    }

    /**
     * Constructs a new ComparableURLClassLoader for the specified URLs using the specified delegation parent
     * <code>ClassLoader</code>.
     * 
     * @param urls the URLs from which to load classes and resources
     * @param parent the parent class loader for delegation
     * @exception SecurityException if a security manager exists and its <code>checkCreateClassLoader</code> method
     *                doesn't allow creation of a class loader or its <code>checkPermission</code> method does not allow
     *                the ReflectPermission("suppressAccessChecks") permission.
     * @see java.net.URLClassLoader
     */
    public ComparableURLClassLoader(URL[] urls, ClassLoader parent)
    {
        super(urls, parent);        
    }

    /**
     * Constructs a new ComparableURLClassLoader for the specified URLs using the specified delegation parent
     * <code>ClassLoader</code>.
     * 
     * @param urls the URLs from which to load classes and resources
     * @param parent the parent class loader for delegation
     * @param factory the URLStreamHandlerFactory to use when creating URLs
     * @exception SecurityException if a security manager exists and its <code>checkCreateClassLoader</code> method
     *                doesn't allow creation of a class loader or its <code>checkPermission</code> method does not allow
     *                the ReflectPermission("suppressAccessChecks") permission.
     * @see java.net.URLClassLoader
     */
    public ComparableURLClassLoader(URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory)
    {
        super(urls, parent, factory);
        FactoryHashCode = factory.hashCode();
    }
    
    /**
     * Ensure comparability by recomputing URL Hash
     * @see java.net.URLClassLoader#addURL(java.net.URL)
     */
    @Override
    protected void addURL(URL url)
    {
        super.addURL(url);
        URLHashCode = 0;
    }

    /**
     * @return true if this class loader should be refreshed by checking its outdated status.
     */
    public boolean isVolatile()
    {
        return isVolatile;
    }

    /**
     * @param isVolatile set to false to avoid check of the outdated status of this class loader.
     */
    public void setVolatile(boolean isVolatile)
    {
        this.isVolatile = isVolatile;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
            return true;
        if( obj == null || (obj.getClass() != this.getClass()))
            return false;
        return( this.hashCode() == obj.hashCode() );
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode()
    {
        if( URLHashCode == 0 )
            URLHashCode = getURLHashCode(super.getURLs());
        
        return getHashCode(URLHashCode, ((getParent() != null) ? getParent().hashCode() : 0), FactoryHashCode);
    }

    /**
     * Compute the hashcode for a ComparableURLClassLoader that would be created with the
     * given arguments
     * @param urls the URLs from which to load classes and resources
     * @return hash code that would be return by such a ComparableURLClassLoader
     * @see java.net.URLClassLoader
     */
    public static int getHashCode(URL[] urls) {
        return getHashCode(urls, getSystemClassLoader(), null);
    }

    /**
     * Compute the hashcode for a ComparableURLClassLoader that would be created with the
     * given arguments
     * @param urls the URLs from which to load classes and resources
     * @param parent The parent class loader     
     * @return hash code that would be return by such a ComparableURLClassLoader
     * @see java.net.URLClassLoader
     */
    public static int getHashCode(URL[] urls, ClassLoader parent) {
        return getHashCode(urls, parent, null);        
    }

    /**
     * Compute the hashcode for a ComparableURLClassLoader that would be created with the
     * given arguments
     * @param urls the URLs from which to load classes and resources
     * @param parent The parent class loader     
     * @param factory the URLStreamHandlerFactory to use when creating URLs
     * @return hash code that would be return by such a ComparableURLClassLoader
     * @see java.net.URLClassLoader
     */
    public static int getHashCode(URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
        return getHashCode(getURLHashCode(urls), ((parent != null) ? parent.hashCode() : 0), ((factory != null) ? factory.hashCode() : 0));
    }
    
    /**
     * Compute hashcode representing a ComparableURLClassLoader based on hashcode of their respective arguments
     * @param urlHashCode hash value representing the URL list
     * @param parentHashCode hash value representing the parent class loader
     * @param factoryHashCode hash value representing the custom URL factory
     * @return a hashcode representing a ComparableURLClassLoader with same URLs
     */
    private static int getHashCode( long urlHashCode, int parentHashCode, int factoryHashCode) {
        return ((int)(urlHashCode & 0xFFFFFFFF)) ^ ((int)(urlHashCode >> 32)) ^ parentHashCode ^ factoryHashCode;
    }

    /**
     * Compute hashcode representing the ordered list of URL used by the URLClassLoader.
     * 
     * @param urls the array of url to be added
     * @return a hashcode corresponding to the provided list of URL
     * @see AddURLtoHash(URL)
     */
    private static long getURLHashCode(URL[] urls)
    {
        long hash = 0;
        for( URL url : urls ) {
            hash = AddURLtoHash( hash, url );
        }
        return hash;
    }

    /**
     * Compute hashcode representing the provided URL. hashing is done using the PJW
     * hash function described in The book Compilers (Principles, Techniques and Tools) by Aho, Sethi and Ulman,
     * optimized for the size of a Java Long.
     * 
     * @param url the url to be added
     * @return a hashcode representing the provided URL.
     */
    private static long AddURLtoHash(long hash, URL url)
    {
        if( url == null )
            return hash;
        
        long overflow = 0;
        String urlstr = url.toString();
        
        for (int i = 0; i < urlstr.length(); i++) {
            hash <<= 8;
            hash += urlstr.charAt(i);

            if ((overflow = hash >> 56) != 0) {
                hash ^= (overflow << 8);
                hash &= 0xFFFFFFFFFFFFFFL;
            }
        }
        
        return hash;
    }
}
