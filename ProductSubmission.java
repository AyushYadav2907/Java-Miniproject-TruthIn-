package com.truthscan.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A product typed in by a user from the label. It is reviewed by an admin before going live. */
public record ProductSubmission(
        @NotBlank
        @Pattern(regexp = "\\d{8,14}", message = "Barcode must be 8 to 14 digits")
        String barcode,

        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String brand,
        @Size(max = 255) String category,
        @Size(max = 5000) String ingredientsText,
        @Size(max = 1000) String allergens,

        @DecimalMin("0") @DecimalMax("900") Double energyKcal,
        @DecimalMin("0") @DecimalMax("100") Double sugar,
        @DecimalMin("0") @DecimalMax("100") Double fat,
        @DecimalMin("0") @DecimalMax("100") Double saturatedFat,
        @DecimalMin("0") @DecimalMax("100") Double salt,
        @DecimalMin("0") @DecimalMax("100") Double fiber,
        @DecimalMin("0") @DecimalMax("100") Double protein,

        @Email @Size(max = 255) String submittedBy
) {
}
