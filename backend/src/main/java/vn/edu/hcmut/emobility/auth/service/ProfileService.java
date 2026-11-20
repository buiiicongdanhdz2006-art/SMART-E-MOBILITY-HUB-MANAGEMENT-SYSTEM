package vn.edu.hcmut.emobility.auth.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.emobility.auth.entity.Account;
import vn.edu.hcmut.emobility.auth.entity.User;
import vn.edu.hcmut.emobility.auth.repository.AccountRepository;
import vn.edu.hcmut.emobility.booking.entity.Booking;
import vn.edu.hcmut.emobility.booking.repository.BookingRepository;
import vn.edu.hcmut.emobility.common.enums.BookingStatus;
import vn.edu.hcmut.emobility.common.enums.VehicleType;
import vn.edu.hcmut.emobility.common.exception.ApiException;
import vn.edu.hcmut.emobility.parking.entity.PersonalVehicle;
import vn.edu.hcmut.emobility.parking.repository.PersonalVehicleRepository;

/** Business logic of ProfileController and VehicleController (F-01: UC-F01-05 .. UC-F01-08). */
@Service
public class ProfileService {

    private final AccountRepository accounts;
    private final BookingRepository bookings;
    private final PersonalVehicleRepository personalVehicles;

    public ProfileService(AccountRepository accounts, BookingRepository bookings, PersonalVehicleRepository personalVehicles) {
        this.accounts = accounts;
        this.bookings = bookings;
        this.personalVehicles = personalVehicles;
    }

    public record ProfileData(Long id, String email, String phone, String studentId, String fullName, String role,
                              String status, List<PersonalVehicle> vehicles, List<Booking> activeBookings) {}

    /** UC-F01-05: account information + personal vehicles + active bookings. */
    @Transactional(readOnly = true)
    public ProfileData getProfile(Account account) {
        List<PersonalVehicle> vehicles = account instanceof User ? personalVehicles.findByOwner_Id(account.getId()) : List.of();
        List<Booking> active = account instanceof User ? getActiveBookings(account.getId()) : List.of();
        return new ProfileData(account.getId(), account.getEmail(), account.getPhone(), account.getStudentId(),
                account.getFullName(), account.getRole().name(), account.getStatus().name(), vehicles, active);
    }

    /** UC-F01-06: validates, checks the phone is not used by another account and saves. */
    @Transactional
    public ProfileData updateProfile(Account current, Map<String, String> data) {
        Map<String, String> toCheck = new HashMap<>();
        if (data.get("fullName") != null) toCheck.put("fullName", data.get("fullName"));
        if (data.get("phone") != null) toCheck.put("phone", data.get("phone"));
        List<String> errors = AuthService.validate(toCheck);
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        if (toCheck.containsKey("phone") && accounts.existsPhone(toCheck.get("phone").trim(), current.getId())) {
            throw ApiException.conflict("Số điện thoại đã được tài khoản khác sử dụng");
        }
        Account account = accounts.findById(current.getId()).orElseThrow(() -> ApiException.notFound("tài khoản"));
        account.update(toCheck);
        accounts.save(account);
        return getProfile(account);
    }

    /** UC-F01-07: ended bookings of the last days (30 by default), newest first. */
    @Transactional(readOnly = true)
    public List<Booking> getHistory(Long userId, int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days <= 0 ? 30 : days);
        List<Booking> ended = bookings.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED || b.getStatus() == BookingStatus.CANCELLED
                        || b.getStatus() == BookingStatus.EXPIRED)
                .filter(b -> b.getEndedAt() != null && !b.getEndedAt().isBefore(from))
                .toList();
        return sortNewestFirst(ended);
    }

    /** UC-F01-07: detail of one booking of the signed-in user. */
    @Transactional(readOnly = true)
    public Booking getBookingDetail(Long userId, Long bookingId) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("lượt đặt xe"));
        if (!b.getUserId().equals(userId)) throw ApiException.forbidden("Không phải lượt đặt xe của bạn");
        return b;
    }

    /** UC-F01-08: registers a personal vehicle after checking format and duplicate plate number. */
    @Transactional
    public PersonalVehicle registerVehicle(Long userId, VehicleType type, String plate, String brand) {
        List<String> errors = new ArrayList<>(AuthService.validate(Map.of("plate", plate == null ? "" : plate)));
        if (type == null) errors.add("Loại xe là bắt buộc");
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        String normalized = AuthService.normalizePlate(plate);
        if (personalVehicles.existsByPlateIgnoreCase(normalized)) throw ApiException.conflict("Biển số đã được đăng ký");
        User user = (User) accounts.findById(userId).orElseThrow(() -> ApiException.notFound("người dùng"));
        PersonalVehicle v = new PersonalVehicle(user, type, normalized, brand == null ? null : brand.trim());
        user.addVehicle(v);
        return personalVehicles.save(v);
    }

    public List<PersonalVehicle> getVehicles(Long userId) { return personalVehicles.findByOwner_Id(userId); }

    List<Booking> getActiveBookings(Long userId) {
        return bookings.findByUser_IdAndStatusInOrderByPickupTimeAsc(userId, List.of(BookingStatus.RESERVED, BookingStatus.IN_PROGRESS));
    }

    static List<Booking> sortNewestFirst(List<Booking> list) {
        List<Booking> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparing(Booking::getEndedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return sorted;
    }
}
