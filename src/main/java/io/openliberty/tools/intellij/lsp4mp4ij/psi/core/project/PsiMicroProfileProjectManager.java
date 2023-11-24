/*******************************************************************************
* Copyright (c) 2020 Red Hat Inc. and others.
* All rights reserved. This program and the accompanying materials
* which accompanies this distribution, and is available at
* https://www.eclipse.org/legal/epl-v20.html
*
* Contributors:
*     Red Hat Inc. - initial API and implementation
*******************************************************************************/
package io.openliberty.tools.intellij.lsp4mp4ij.psi.core.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.BulkAwareDocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import io.openliberty.tools.intellij.lsp4mp4ij.classpath.ClasspathResourceChangedManager;
import io.openliberty.tools.intellij.lsp4mp4ij.psi.internal.core.ls.PsiUtilsLSImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * {@link PsiMicroProfileProject} manager.
 *
 * @author Angelo ZERR
 * @see <a href="https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/core/project/JDTMicroProfileProjectManager.java">https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/core/project/JDTMicroProfileProjectManager.java</a>
 */
public final class PsiMicroProfileProjectManager implements Disposable {

	private static final Key<PsiMicroProfileProject> KEY = new Key<>(PsiMicroProfileProject.class.getName());

	private static final String JAVA_FILE_EXTENSION = "java";

	public static PsiMicroProfileProjectManager getInstance(Project project) {
		return ServiceManager.getService(project, PsiMicroProfileProjectManager.class);
	}

	private final MessageBusConnection connection;

	private final Project project;

	private final MicroProfileProjectListener microprofileProjectListener;

	private class MicroProfileProjectListener implements ModuleListener, ClasspathResourceChangedManager.Listener, BulkFileListener, BulkAwareDocumentListener.Simple, Disposable {
		@Override
		public void librariesChanged() {
			// Do nothing
		}

		@Override
		public void sourceFilesChanged(Set<Pair<VirtualFile, Module>> sources) {
			for (var pair : sources) {
				VirtualFile file = pair.getFirst();
				if (isConfigSource(file)) {
					// A microprofile config file properties file source has been updated, evict the cache of the properties
					Module javaProject = pair.getSecond();
					PsiMicroProfileProject mpProject = getMicroProfileProject(javaProject);
					if (mpProject != null) {
						mpProject.evictConfigSourcesCache(file);
					}
				}
			}
		}

		@Override
		public void after(@NotNull List<? extends VFileEvent> events) {
			for(VFileEvent event : events) {
				if ((event instanceof VFileDeleteEvent || event instanceof VFileContentChangeEvent ||
						event instanceof VFileCreateEvent) && isConfigSource(event.getFile())) {
					processChangedConfigSource(event.getFile());
				}
			}
		}

		@Override
		public void afterDocumentChange(@NotNull Document document) {
			final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
			if (file != null && isConfigSource(file)) {
				processChangedConfigSource(file);
			}
		}

		@Override
		public void beforeModuleRemoved(@NotNull Project project, @NotNull Module module) {
			evict(module);
		}

		private void processChangedConfigSource(VirtualFile file) {
			Module javaProject = PsiUtilsLSImpl.getInstance(project).getModule(file);
			if (javaProject != null) {
				PsiMicroProfileProject mpProject = getMicroProfileProject(javaProject);
				if (mpProject != null) {
					mpProject.evictConfigSourcesCache(null);
				}
			}
		}

		private void evict(Module javaProject) {
			if (javaProject != null) {
				// Remove the JDTMicroProfile project instance from the cache.
				removeMicroProfileProject(javaProject);
			}
		}

		@Override
		public void dispose() {}
	}

	private PsiMicroProfileProjectManager(Project project) {
		this.project = project;
		microprofileProjectListener = new MicroProfileProjectListener();
		connection = project.getMessageBus().connect(project);
		connection.subscribe(ClasspathResourceChangedManager.TOPIC, microprofileProjectListener);
		connection.subscribe(ProjectTopics.MODULES, microprofileProjectListener);
		EditorFactory.getInstance().getEventMulticaster().addDocumentListener(microprofileProjectListener, microprofileProjectListener);
	}

	public PsiMicroProfileProject getMicroProfileProject(Module project) {
		return getMicroProfileProject(project, true);
	}

	private PsiMicroProfileProject getMicroProfileProject(Module javaProject, boolean create) {
		PsiMicroProfileProject mpProject = javaProject.getUserData(KEY);
		if (mpProject == null) {
			if (!create) {
				return null;
			}
			mpProject = new PsiMicroProfileProject(javaProject);
			javaProject.putUserData(KEY, mpProject);
		}
		return mpProject;
	}

	/**
	 * Returns true if the given file is a MicroProfile config properties file (microprofile-config.properties, application.properties, application.yaml, etc) and false otherwise.
	 *
	 * @param file the file to check.
	 * @return true if the given file is a MicroProfile config properties file (microprofile-config.properties, application.properties, application.yaml, etc) and false otherwise.
	 */
	public static boolean isConfigSource(VirtualFile file) {
		if (file == null) {
			return false;
		}
		String fileName = file.getName();
		for (IConfigSourceProvider provider : IConfigSourceProvider.EP_NAME.getExtensions()) {
			if (provider.isConfigSource(fileName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if the given file is a Java file and false otherwise.
	 *
	 * @param file the file to check.
	 * @return true if the given file is a Java file and false otherwise.
	 */
	public static boolean isJavaFile(VirtualFile file) {
		return file != null && JAVA_FILE_EXTENSION.equals(file.getExtension());
	}

	@Override
	public void dispose() {
		Module[] modules = ModuleManager.getInstance(project).getModules();
		for (Module module : modules) {
			removeMicroProfileProject(module);
		}
		connection.disconnect();
	}

	private static void removeMicroProfileProject(Module module) {
		module.putUserData(KEY, null);
	}
}
