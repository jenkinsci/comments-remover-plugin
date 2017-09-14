package com.ste.comments.remover;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.zeroturnaround.zip.ZipUtil;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.*;
import java.net.URL;

public class CommentsRemoverBuilder extends Builder implements SimpleBuildStep {

    private final static String COMMENTS_REMOVER_ARCHIVE_NAME = "comments_remover.zip";
    private final static String COMMENTS_REMOVER_ENTRY_FILE = "comments_remover.py";

    private final String filename;
    private final String language;
    private final String outputDir;

    @DataBoundConstructor
    public CommentsRemoverBuilder(String filename, String language, String outputDir) {
        this.filename = filename;
        this.language = language;
        this.outputDir = outputDir;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("Invoking Comments Remover for filename: " + filename + " and language: " + language);
        File outputDirPath = setupOutputDir(workspace, outputDir);
        runCommentsRemoverProcess(listener, workspace, getDescriptor().getCommentRemoverDir(), outputDirPath);
        listener.getLogger().println("Comments Remover finished processing. Output saved to directory: " + outputDirPath.getAbsoluteFile());
    }

    private File setupOutputDir(FilePath workspace, String outputDir) throws IOException {
        File outputDirPath = new File(workspace.getRemote() + File.separator + outputDir);
        if (outputDirPath.exists()) {
            FileUtils.deleteDirectory(outputDirPath);
        }
        outputDirPath.mkdir();
        return outputDirPath;
    }

    private void runCommentsRemoverProcess(TaskListener listener, FilePath workspace, File
            removerScriptDir, File outputDirPath) throws IOException {

        String pythonPath = getDescriptor().getPythonPath();
        String pipPath = getDescriptor().getPipPath();

        String pipExecutable = StringUtils.isEmpty(pythonPath) ? "pip" : pipPath;
        String[] pipCommand = new String[]{pipExecutable, "install", "-r", removerScriptDir + File.separator + "requirements.txt", "-q"};
        listener.getLogger().println("Installing pip requirements [" + StringUtils.join(pipCommand, " ") + "]...");
        runProcess(new ProcessBuilder(pipExecutable, "install", "-r", removerScriptDir + File.separator + "requirements.txt", "-q"),
                listener);

        String pythonExecutable = StringUtils.isEmpty(pythonPath) ? "python" : pythonPath;
        String[] commentsRemoverCommand = new String[]{pythonExecutable, removerScriptDir + File.separator + COMMENTS_REMOVER_ENTRY_FILE,
                workspace.getRemote() + File.separator + filename, language, outputDirPath.getAbsolutePath()};
        listener.getLogger().println("Executing script [" + StringUtils.join(commentsRemoverCommand, " ") + "]...");
        runProcess(new ProcessBuilder(commentsRemoverCommand), listener);
    }

    private void runProcess(ProcessBuilder pb, TaskListener listener) throws IOException {
        pb.redirectErrorStream(true);

        Process proc = pb.start();

        /* Read the process's output */
        String line;
        BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        while ((line = in.readLine()) != null) {
            listener.getLogger().println(line);
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private String pythonPath;
        private String pipPath;
        private File commentRemoverDir;

        public DescriptorImpl() throws IOException {
            load();
            unzipCommentsRemover();
        }

        private void unzipCommentsRemover() throws IOException {
            File jenkinsRootDir = Jenkins.getActiveInstance().getRootDir();
            File removerTargetDir = new File(jenkinsRootDir.getAbsolutePath() + File.separator + "comments_remover");
            if (removerTargetDir.exists()) {
                System.out.println("Removing old Comments Remover from: " + removerTargetDir);
                removerTargetDir.delete();
            }

            URL removerZipUrl = CommentsRemoverBuilder.class.getClassLoader().getResource(COMMENTS_REMOVER_ARCHIVE_NAME);
            if (removerZipUrl == null) {
                throw new RuntimeException("Failed to find Comments Remover archive in classpath (searched for name: " +
                        COMMENTS_REMOVER_ARCHIVE_NAME + "), failed to initialize Comments Remover plugin");
            }
            File removerZip = copyZipFromResources(removerZipUrl, jenkinsRootDir);
            System.out.println("Unpacking Comments Remover from: " + removerZipUrl.getFile() + " to " + removerTargetDir);
            ZipUtil.unpack(removerZip, removerTargetDir);
            removerZip.delete();
            commentRemoverDir = removerTargetDir;
        }

        private File copyZipFromResources(URL removerUrl, File targetDir) throws IOException {
            InputStream in = new BufferedInputStream(removerUrl.openStream());
            File zip = File.createTempFile("remover", ".zip", targetDir);
            OutputStream out = new BufferedOutputStream(new FileOutputStream(zip));
            copyInputStream(in, out);
            out.close();
            return zip;
        }

        private static void copyInputStream(InputStream in, OutputStream out) throws IOException {
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);
            while (len >= 0) {
                out.write(buffer, 0, len);
                len = in.read(buffer);
            }
            in.close();
            out.close();
        }

        public FormValidation doCheckFilename(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a filename");
            return FormValidation.ok();
        }

        public FormValidation doCheckLanguage(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a language");
            return FormValidation.ok();
        }

        public FormValidation doCheckOutputDir(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set output directory");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Invoke Comments Remover";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            pythonPath = formData.getString("pythonPath");
            pipPath = formData.getString("pipPath");
            save();
            return super.configure(req, formData);
        }

        String getPythonPath() {
            return pythonPath;
        }

        String getPipPath() {
            return pipPath;
        }

        File getCommentRemoverDir() {
            return commentRemoverDir;
        }
    }

    public String getFilename() {
        return filename;
    }

    public String getLanguage() {
        return language;
    }

    public String getOutputDir() {
        return outputDir;
    }
}

