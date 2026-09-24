package com.deploykit.security;

/** Tells services who is calling. Abstracted so services stay testable without a security context. */
public interface CurrentUserProvider {

    /** The authenticated caller; only meaningful while handling an authenticated request. */
    CurrentUser require();
}
