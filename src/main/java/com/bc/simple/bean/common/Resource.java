package com.bc.simple.bean.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.support.CurrencyException;

public class Resource {

	private final String path;

	private final File file;

	private final Path filePath;
	/**
	 * Original URI, if available; used for URI and File access.
	 */
	private final URI uri;

	private final Object monitor = new Object();

	private AtomicBoolean opened = new AtomicBoolean(false);


	public Resource(File file) {
		this.path = StringUtils.cleanPath(file.getPath());
		this.file = file;
		this.filePath = file.toPath();
		this.uri = file.toURI();
	}

	public Resource(String path) {
		this.path = StringUtils.cleanPath(path);
		this.file = new File(this.path);
		this.filePath = this.file.toPath();
		this.uri = this.file.toURI();
	}

	public Resource(URL url) throws URISyntaxException {
		this.uri = url.toURI();
		this.file = new File(this.uri);
		this.filePath = this.file.toPath();
		this.path = StringUtils.cleanPath(this.file.getPath());
	}

	/**
	 * Determine whether this resource actually exists in physical form.
	 * <p>
	 * This method performs a definitive existence check, whereas the existence of a
	 * {@code Resource} handle only guarantees a valid descriptor handle.
	 */
	public boolean exists() {
		return (this.file != null ? this.file.exists() : Files.exists(this.filePath));
	}

	/**
	 * Indicate whether non-empty contents of this resource can be read via
	 * {@link #getInputStream()}.
	 * <p>
	 * Will be {@code true} for typical resource descriptors that exist since it strictly implies
	 * {@link #exists()} semantics as of 5.1. Note that actual content reading may still fail when
	 * attempted. However, a value of {@code false} is a definitive indication that the resource
	 * content cannot be read.
	 * 
	 * @see #getInputStream()
	 * @see #exists()
	 */
	public boolean isReadable() {
		return exists();
	}

	/**
	 * Indicate whether this resource represents a handle with an open stream. If {@code true}, the
	 * InputStream cannot be read multiple times, and must be read and closed to avoid resource
	 * leaks.
	 * <p>
	 * Will be {@code false} for typical resource descriptors.
	 */
	public boolean isOpen() {
		synchronized (monitor) {
			return opened.get();
		}
	}

	/**
	 * Determine whether this resource represents a file in a file system. A value of {@code true}
	 * strongly suggests (but does not guarantee) that a {@link #getFile()} call will succeed.
	 * <p>
	 * This is conservatively {@code false} by default.
	 * 
	 * @since 5.0
	 * @see #getFile()
	 */
	public boolean isFile() {
		return true;
	}


	/**
	 * Return a URI handle for this resource.
	 * 
	 * @throws IOException if the resource cannot be resolved as URI, i.e. if the resource is not
	 *         available as descriptor
	 * @since 2.5
	 */
	public URI getURI() {
		return this.uri;
	}

	/**
	 * Return a File handle for this resource.
	 * 
	 * @throws java.io.FileNotFoundException if the resource cannot be resolved as absolute file
	 *         path, i.e. if the resource is not available in a file system
	 * @throws IOException in case of general resolution/reading failures
	 * @see #getInputStream()
	 */
	public File getFile() {
		return this.file;
	}

	/**
	 * Return a {@link ReadableByteChannel}.
	 * <p>
	 * It is expected that each call creates a <i>fresh</i> channel.
	 * <p>
	 * The default implementation returns {@link Channels#newChannel(InputStream)} with the result
	 * of {@link #getInputStream()}.
	 * 
	 * @return the byte channel for the underlying resource (must not be {@code null})
	 * @throws java.io.FileNotFoundException if the underlying resource doesn't exist
	 * @throws IOException if the content channel could not be opened
	 * @since 5.0
	 * @see #getInputStream()
	 */
	public ReadableByteChannel readableChannel() {
		return Channels.newChannel(getInputStream());
	}

	/**
	 * Determine the content length for this resource.
	 * 
	 * @throws IOException if the resource cannot be resolved (in the file system or as some other
	 *         known physical resource type)
	 */
	public long contentLength() {
		try {
			return (this.file != null ? this.file.length() : Files.size(this.filePath));
		} catch (IOException e) {
			// ignore
		}
		return -1;
	}

	/**
	 * Determine the last-modified timestamp for this resource.
	 * 
	 * @throws IOException if the resource cannot be resolved (in the file system or as some other
	 *         known physical resource type)
	 */
	public long lastModified() {
		return this.file.lastModified();
	}

	/**
	 * Create a resource relative to this resource.
	 * 
	 * @param relativePath the relative path (relative to this resource)
	 * @return the resource handle for the relative resource
	 * @throws IOException if the relative resource cannot be determined
	 */
	public Resource createRelative(String relativePath) {
		String pathToUse = StringUtils.applyRelativePath(this.path, relativePath);
		return new Resource(pathToUse);
	}

	/**
	 * Determine a filename for this resource, i.e. typically the last part of the path: for
	 * example, "myfile.txt".
	 * <p>
	 * Returns {@code null} if this type of resource does not have a filename.
	 */

	public String getFilename() {
		return (this.file != null ? this.file.getName() : this.filePath.getFileName().toString());
	}

	/**
	 * Return a description for this resource, to be used for error output when working with the
	 * resource.
	 * <p>
	 * Implementations are also encouraged to return this value from their {@code toString} method.
	 * 
	 * @see Object#toString()
	 */
	public String getDescription() {
		return "file [" + (this.file != null ? this.file.getAbsolutePath() : this.filePath.toAbsolutePath()) + "]";

	}

	/**
	 * Return an {@link InputStream} for the content of an underlying resource.
	 * <p>
	 * It is expected that each call creates a <i>fresh</i> stream.
	 * <p>
	 * This requirement is particularly important when you consider an API such as JavaMail, which
	 * needs to be able to read the stream multiple times when creating mail attachments. For such a
	 * use case, it is <i>required</i> that each {@code getInputStream()} call returns a fresh
	 * stream.
	 * 
	 * @return the input stream for the underlying resource (must not be {@code null})
	 * @throws java.io.FileNotFoundException if the underlying resource doesn't exist
	 * @throws IOException if the content stream could not be opened
	 */
	public InputStream getInputStream() {
		try {
			return Files.newInputStream(this.filePath);
		} catch (NoSuchFileException ex) {
			throw new CurrencyException("no such file!", ex);
		} catch (IOException e) {
			// ignore
		}
		return null;
	}


	public URI getUri() {
		return uri;
	}

	public AtomicBoolean getOpened() {
		return opened;
	}

	public void setOpened(AtomicBoolean opened) {
		this.opened = opened;
	}

	public String getPath() {
		return path;
	}

	public Path getFilePath() {
		return filePath;
	}

	/**
	 * This implementation returns the description of this resource.
	 * 
	 * @see #getDescription()
	 */
	@Override
	public String toString() {
		return getDescription();
	}


}

