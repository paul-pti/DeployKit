package com.deploykit.domain;

import java.util.regex.Pattern;

/** A container image as repository + tag (digests are not supported by the Helm chart). */
public record ImageReference(String repository, String tag) {

    private static final String COMPONENT = "[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*";
    private static final Pattern REPOSITORY =
            Pattern.compile("^" + COMPONENT + "(?::[0-9]+)?(?:/" + COMPONENT + ")*$");
    private static final Pattern TAG = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$");
    private static final Pattern COMMIT_SHA_TAG = Pattern.compile("^[0-9a-f]{7,64}$");

    public ImageReference {
        if (repository == null || repository.length() > 400 || !REPOSITORY.matcher(repository).matches()) {
            throw new IllegalArgumentException("Invalid image repository: " + repository);
        }
        if (tag == null || !TAG.matcher(tag).matches()) {
            throw new IllegalArgumentException("Invalid image tag: " + tag);
        }
    }

    /** Parses {@code repository[:tag]}; the tag defaults to {@code latest} like Docker does. */
    public static ImageReference parse(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Image must not be blank");
        }
        String ref = reference.trim();
        if (ref.contains("@")) {
            throw new IllegalArgumentException("Image digests are not supported, use a tag: " + ref);
        }
        int lastSlash = ref.lastIndexOf('/');
        int colon = ref.indexOf(':', lastSlash + 1);
        if (colon < 0) {
            return new ImageReference(ref, "latest");
        }
        return new ImageReference(ref.substring(0, colon), ref.substring(colon + 1));
    }

    /** True when the tag is a commit SHA (7 to 64 lowercase hex characters). */
    public boolean isCommitSha() {
        return COMMIT_SHA_TAG.matcher(tag).matches();
    }

    /** Commit-SHA tags are immutable, anything else (branch names, latest) may move and must be re-pulled. */
    public String pullPolicy() {
        return isCommitSha() ? "IfNotPresent" : "Always";
    }

    @Override
    public String toString() {
        return repository + ":" + tag;
    }
}
