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
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.support.SimpleException;

public class Resource {

	private final String path;

	private final File file;

	private final Path filePath;

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


	public boolean exists() {
		return (this.file != null ? this.file.exists() : Files.exists(this.filePath));
	}


	public boolean isReadable() {
		return exists();
	}


	public boolean isOpen() {
		synchronized (monitor) {
			return opened.get();
		}
	}


	public boolean isFile() {
		return true;
	}



	public URI getURI() {
		return this.uri;
	}


	public File getFile() {
		return this.file;
	}


	public ReadableByteChannel readableChannel() {
		return Channels.newChannel(getInputStream());
	}


	public long contentLength() {
		try {
			return (this.file != null ? this.file.length() : Files.size(this.filePath));
		} catch (IOException e) {
			// ignore
		}
		return -1;
	}


	public long lastModified() {
		return this.file.lastModified();
	}


	public Resource createRelative(String relativePath) {
		String pathToUse = StringUtils.applyRelativePath(this.path, relativePath);
		return new Resource(pathToUse);
	}



	public String getFilename() {
		return (this.file != null ? this.file.getName() : this.filePath.getFileName().toString());
	}


	public String getDescription() {
		return "file [" + (this.file != null ? this.file.getAbsolutePath() : this.filePath.toAbsolutePath()) + "]";

	}


	public InputStream getInputStream() {
		try {
			return Files.newInputStream(this.filePath);
		} catch (Exception e) {
			throw new SimpleException("no such file!", e);
		}
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


	@Override
	public String toString() {
		return getDescription();
	}


}

