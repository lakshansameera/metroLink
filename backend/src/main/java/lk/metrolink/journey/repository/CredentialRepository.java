package lk.metrolink.journey.repository;

import java.util.Optional;
import lk.metrolink.journey.domain.PasswordHash;

/**
 * Where password hashes are kept, apart from the passenger's profile. Nothing here ever holds a password itself
 * (rule O6).
 */
public interface CredentialRepository {

    /** Stores the hash for a passenger who has none yet. */
    void insert(long passengerId, PasswordHash hash);

    Optional<PasswordHash> findByPassenger(long passengerId);
}
