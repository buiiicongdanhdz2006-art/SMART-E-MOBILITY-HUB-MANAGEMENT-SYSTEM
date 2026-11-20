package vn.edu.hcmut.emobility.simulation.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationResult {

    private final List<String> errors = new ArrayList<>();

    /** True if no errors have been recorded. */
    public boolean isValid() { return errors.isEmpty(); }

    public void addError(String message) { errors.add(message); }

    public List<String> getErrors() { return Collections.unmodifiableList(errors); }
}
