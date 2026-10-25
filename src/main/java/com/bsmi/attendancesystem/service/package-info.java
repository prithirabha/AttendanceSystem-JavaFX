/**
 * The rules between the screens and the DAOs: login with bcrypt, form validation, who may change
 * what, storing student photos, drawing and reading QR student cards, and password-locked backups
 * and restores. A broken rule throws a subclass of {@link
 * com.bsmi.attendancesystem.service.ValidationException} whose message is ready to show the user.
 */
package com.bsmi.attendancesystem.service;
