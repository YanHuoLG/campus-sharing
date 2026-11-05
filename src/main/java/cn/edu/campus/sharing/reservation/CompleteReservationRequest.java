package cn.edu.campus.sharing.reservation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CompleteReservationRequest(@NotNull @Pattern(regexp="[0-9]{6}") String code) {}
