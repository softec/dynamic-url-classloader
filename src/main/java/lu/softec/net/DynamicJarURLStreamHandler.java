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
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * This is wrapper class of <code>URLStreamHandler</code> for the jar protocol that allow fine grained control over
 * jar caching and closing.
 * 
 * @author Denis Gervalle (support@softec.lu)
 * @see lu.softec.net.DynamicURLStreamHandlerFactory
 * @see java.net.URLStreamHandler
 */
class DynamicJarURLStreamHandler extends URLStreamHandler
{
    private URLStreamHandler handler;
    private Method openConnection = null;
    private Method parseURL = null;

    private WeakReference<DynamicURLClassLoader> classLoaderRef = null;


    /**
     * Constructs a new DynamicJarURLStreamHandler that wrap <code>URLStreamHandler</code> for the Jar protocol to
     * controls caching and closing of Jar files.
     * 
     * @param handler the URLStreamHandler to use when opening URLs
     * @exception SecurityException if a security manager exists and its <code>checkPermission</code> method does not
     *                allow the ReflectPermission("suppressAccessChecks") permission.
     * @see lu.softec.net.DynamicURLStreamHandlerFactory#createURLStreamHandler(String)
     */
    DynamicJarURLStreamHandler(URLStreamHandler handler) throws SecurityException
    {
        this.handler = handler;
        try {
            this.openConnection = handler.getClass().getDeclaredMethod("openConnection", new Class[] {URL.class});
            this.parseURL =
                handler.getClass().getDeclaredMethod("parseURL",
                    new Class[] {URL.class, String.class, Integer.class, Integer.class});
        } catch (NoSuchMethodException e) {
        }
        if (this.openConnection != null)
            this.openConnection.setAccessible(true);
        if (this.parseURL != null)
            this.parseURL.setAccessible(true);
    }

    /**
     * Opens a connection to the object referenced by the <code>URL</code> argument using the wrapped jar handler and
     * set the UseCaches field of the created <code>URLConnection</code> to false to avoid reusage of JarFile. Also keep
     * track of this URLConnection to be able to close the created Jar file on request.
     * 
     * @param u the URL that this connects to.
     * @return a <code>URLConnection</code> object for the <code>URL</code>.
     * @exception IOException if an I/O error occurs while opening the connection.
     * @see java.net.URLStreamHandler#openConnection(URL)
     */
    protected URLConnection openConnection(URL u) throws IOException
    {
        JarURLConnection uc = null;
        try {
            if (this.openConnection != null) {
                uc = (JarURLConnection) this.openConnection.invoke(handler, u);
                DynamicURLClassLoader classLoader = null;
                if (classLoaderRef != null)
                    classLoader = classLoaderRef.get();

                uc = new DynamicJarURLConnection(uc, classLoader);
            }
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
        return uc;
    }

    /**
     * Parses the string representation of a <code>URL</code> into a <code>URL</code> object.
     * <p>
     * If there is any inherited context, then it has already been copied into the <code>URL</code> argument.
     * <p>
     * The <code>parseURL</code> method of <code>URLStreamHandler</code> parses the string representation as if it were
     * an <code>http</code> specification. Most URL protocol families have a similar parsing. A stream protocol handler
     * for a protocol that has a different syntax must override this routine.
     * 
     * @param u the <code>URL</code> to receive the result of parsing the spec.
     * @param spec the <code>String</code> representing the URL that must be parsed.
     * @param start the character index at which to begin parsing. This is just past the '<code>:</code>' (if there is
     *            one) that specifies the determination of the protocol name.
     * @param limit the character position to stop parsing at. This is the end of the string or the position of the "
     *            <code>#</code>" character, if present. All information after the sharp sign indicates an anchor.
     */
    protected void parseURL(URL u, String spec, int start, int limit)
    {
        try {
            if (this.parseURL != null)
                this.parseURL.invoke(handler, u, start, limit);
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
    }

    /**
     * @param classLoader the class loader using this handler
     */
    public void setClassLoader(DynamicURLClassLoader classLoader)
    {
        if (classLoader != null)
            this.classLoaderRef = new WeakReference<DynamicURLClassLoader>(classLoader);
    }
}
