package cn.edu.campus.sharing.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReservationRequest(@NotBlank @Size(max=500) String message) {}
