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
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * This a wrapper class of <code>JarURLConnection</code> to properly handle JAR file caching.
 * It allows properly closing and expiring a JAR file when the cached file is outdated.
 * It is used with a DynamicURLClassLoader to allow dynamic reloading of classes.
 *
 * @version $Id: $
 */
public class DynamicJarURLConnection extends JarURLConnection
{
    private static ReadWriteLock jarFilesLock = new ReentrantReadWriteLock();

    private static Lock jarFilesReadLock = jarFilesLock.readLock();

    private static Lock jarFilesWriteLock = jarFilesLock.writeLock();

    private static ConcurrentMap<URL, AtomicInteger> jarFileConnections = new ConcurrentHashMap<URL, AtomicInteger>();

    private JarURLConnection delegate;

    private URL jarFileURL;

    private boolean connected;

    private boolean disconnected;

    private WeakReference<DynamicURLClassLoader> classLoaderRef = null;

    private long lastmodified;

    /*
     * @param delegate the JarURLConnection use to delegate requests
     * @param classLoader the classLoader using this connection
     * @throws MalformedURLException if the URL is not properly formed
     */
    public DynamicJarURLConnection(JarURLConnection delegate, DynamicURLClassLoader classLoader)
        throws MalformedURLException
    {
        super(delegate.getURL());
        jarFileURL = getJarFileURL();
        this.delegate = delegate;
        if (classLoader != null)
            this.classLoaderRef = new WeakReference<DynamicURLClassLoader>(classLoader);
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#connect()
     */
    @Override
    public void connect() throws IOException
    {
        if(!connected) {            
            jarFilesReadLock.lock();
            AtomicInteger counter = new AtomicInteger(0);
            AtomicInteger counter2 = jarFileConnections.putIfAbsent(jarFileURL, counter);
            if (counter2 != null)
                counter = counter2;
            counter.incrementAndGet();
            try {
                delegate.connect();
            } catch(IOException e) {
                if (counter.decrementAndGet() == 0) {
                    jarFilesReadLock.unlock();
                    jarFilesWriteLock.lock();
                    if (counter.get() == 0) {
                        jarFileConnections.remove(jarFileURL);
                    }
                    jarFilesReadLock.lock();
                    jarFilesWriteLock.unlock();
                }
                return;
            } finally {
                jarFilesReadLock.unlock();
            }            
            connected = true;
            lastmodified = delegate.getLastModified();
        } else if( disconnected ) {
            throw new IllegalStateException("Already disconnected");
        }
    }

    /**
     * Close connection and allow file resource to be released and therefore remove from the jar cache. The temporary
     * file will be deleted and the next time this jar is requested, it will be fetched again from the server.
     * @return true if the file has been really closed.
     * @throws java.io.IOException when an I/O error occurs during operation
     */
    public boolean close() throws IOException
    {
        if (!isConnected()) {
            return (jarFileConnections.get(jarFileURL) == null);
        }

        disconnected = true;
        AtomicInteger counter = jarFileConnections.get(jarFileURL);
        if (counter != null && counter.decrementAndGet() == 0) {
            jarFilesWriteLock.lock();
            try {
                if (counter.get() == 0) {
                    delegate.getJarFile().close();
                    jarFileConnections.remove(jarFileURL);
                    return true;
                }
            } finally {
                jarFilesWriteLock.unlock();
            }
        }
        return false;
    }

    /**
     * @return true if this connection is connected
     */
    public boolean isConnected()
    {
        return (connected && !disconnected);
    }

    /**
     * @return true if the Jar File associated to this connection has never been open or has been closed
     */
    public boolean isClosed()
    {
        return jarFileConnections.containsKey(jarFileURL);
    }

    /**
     * @return the number of connections that has currently open the Jar File associated with this connection
     */
    public int connectionsCount()
    {
        AtomicInteger counter = jarFileConnections.get(jarFileURL);
        if (counter != null)
            return counter.intValue();
        return 0;
    }

    /**
     * Check if the last modification date of the jar file associated to this connection is newer than the cached jar
     * file currently in use. For file retrieve through http connection, only a head connection is made, and the server
     * is expected to provide an appropriate last-modified header.
     * 
     * @return true if the last modification date of the remote jar file is newer that the cached jar file used. For a
     *         not connected connection, return false. If any error occurs during date retrieval, return true.
     */
    public boolean isOutdated()
    {
        if (!connected)
            return false;

        long lastmodified;
        try {
            URLConnection uc = jarFileURL.openConnection();
            if (uc instanceof HttpURLConnection) {
                HttpURLConnection httpuc = (HttpURLConnection) uc;
                httpuc.setRequestMethod("HEAD");
                lastmodified = httpuc.getLastModified();
            } else {
                lastmodified = uc.getLastModified();
            }
        } catch (IOException e) {
            lastmodified = Long.MAX_VALUE;
        }
        return (lastmodified > this.lastmodified);
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable
    {
        close();
        super.finalize();
    }

    /*** Connecting required for these function ***/

    /**
     * {@inheritDoc}
     * 
     * @see java.net.JarURLConnection#getJarFile()
     */
    @Override
    public JarFile getJarFile() throws IOException
    {
        connect();
        if (classLoaderRef != null) {
            DynamicURLClassLoader classLoader = classLoaderRef.get();
            if (classLoader != null)
                classLoader.register(this);
        }
        return delegate.getJarFile();
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.JarURLConnection#getJarEntry()
     */
    @Override
    public JarEntry getJarEntry() throws IOException
    {
        connect();
        return delegate.getJarEntry();
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#getInputStream()
     */
    @Override
    public InputStream getInputStream() throws IOException
    {
        connect();
        return delegate.getInputStream();
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#getContent()
     */
    @Override
    public Object getContent() throws IOException
    {
        connect();
        return delegate.getContent();
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#getContentLength()
     */
    @Override
    public int getContentLength()
    {
        long l = getContentLengthLong();
        if (l > Integer.MAX_VALUE)
            return -1;
        return (int) l;
    }

    /**
     * Not annotated override to be compatible with older implementation but prepared for the evolution, when 7.0 will
     * be used
     * 
     * @see java.net.URLConnection#getContentLengthLong()
     */
    public long getContentLengthLong()
    {
        try {
            connect();
        } catch (IOException e) {
        }
        return delegate.getContentLength();
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#getContentType()
     */
    @Override
    public String getContentType()
    {
        if (getEntryName() != null) {
            try {
                connect();
            } catch (IOException e) {
                // don't do anything
            }
        }
        return delegate.getContentType();
    }

    /*** Delegation to the jarFileURLConnection of the delegate ***/

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#getPermission()
     */
    @Override
    public Permission getPermission() throws IOException
    {
        return delegate.getPermission();
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#getHeaderField(java.lang.String)
     */
    @Override
    public String getHeaderField(String name)
    {
        return delegate.getHeaderField(name);
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#addRequestProperty(java.lang.String, java.lang.String)
     */
    @Override
    public void addRequestProperty(String key, String value)
    {
        delegate.addRequestProperty(key, value);
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#getAllowUserInteraction()
     */
    @Override
    public boolean getAllowUserInteraction()
    {
        return delegate.getAllowUserInteraction();
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#getRequestProperties()
     */
    @Override
    public Map<String, List<String>> getRequestProperties()
    {
        return delegate.getRequestProperties();
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#setAllowUserInteraction(boolean)
     */
    @Override
    public void setAllowUserInteraction(boolean allowuserinteraction)
    {
        delegate.setAllowUserInteraction(allowuserinteraction);
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#setRequestProperty(java.lang.String, java.lang.String)
     */
    @Override
    public void setRequestProperty(String key, String value)
    {
        delegate.setRequestProperty(key, value);
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#getDefaultUseCaches()
     */
    @Override
    public boolean getDefaultUseCaches()
    {
        return delegate.getDefaultUseCaches();
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#getUseCaches()
     */
    @Override
    public boolean getUseCaches()
    {
        return delegate.getUseCaches();
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#setDefaultUseCaches(boolean)
     */
    @Override
    public void setDefaultUseCaches(boolean defaultusecaches)
    {
        delegate.setDefaultUseCaches(defaultusecaches);
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#setIfModifiedSince(long)
     */
    @Override
    public void setIfModifiedSince(long ifmodifiedsince)
    {
        delegate.setIfModifiedSince(ifmodifiedsince);
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.net.URLConnection#setUseCaches(boolean)
     */
    @Override
    public void setUseCaches(boolean usecaches)
    {
        delegate.setUseCaches(usecaches);
    }
}
