package org.egovframe.cloud.reservechecksevice.api;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.egovframe.cloud.common.domain.Role;
import org.egovframe.cloud.common.exception.dto.ErrorCode;
import org.egovframe.cloud.common.exception.dto.ErrorResponse;
import org.egovframe.cloud.reservechecksevice.api.reserve.dto.ReserveCancelRequestDto;
import org.egovframe.cloud.reservechecksevice.api.reserve.dto.ReserveListResponseDto;
import org.egovframe.cloud.reservechecksevice.api.reserve.dto.ReserveRequestDto;
import org.egovframe.cloud.reservechecksevice.api.reserve.dto.ReserveResponseDto;
import org.egovframe.cloud.reservechecksevice.api.reserve.dto.ReserveSaveRequestDto;
import org.egovframe.cloud.reservechecksevice.api.reserve.dto.ReserveUpdateRequestDto;
import org.egovframe.cloud.reservechecksevice.client.ReserveItemServiceClient;
import org.egovframe.cloud.reservechecksevice.client.dto.ReserveItemResponseDto;
import org.egovframe.cloud.reservechecksevice.client.dto.UserResponseDto;
import org.egovframe.cloud.reservechecksevice.domain.location.Location;
import org.egovframe.cloud.reservechecksevice.domain.reserve.Reserve;
import org.egovframe.cloud.reservechecksevice.domain.reserve.ReserveItem;
import org.egovframe.cloud.reservechecksevice.domain.reserve.ReserveRepository;
import org.egovframe.cloud.reservechecksevice.domain.reserve.ReserveStatus;
import org.egovframe.cloud.reservechecksevice.util.RestResponsePage;
import org.egovframe.cloud.reservechecksevice.util.WithCustomMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties
@TestPropertySource(properties = {"spring.config.location=classpath:application-test.yml"})
@ActiveProfiles(profiles = "test")
class ReserveApiControllerBDDTest {
	@MockBean
	private ReserveRepository reserveRepository;

	@MockBean
	private ReserveItemServiceClient reserveItemServiceClient;

	@Autowired
	private WebTestClient webTestClient;

	private static final String API_URL = "/api/v1/reserves";

	private UserResponseDto user;
	private Location location;
	private ReserveItem reserveItem;
	private Reserve reserve;

	@BeforeEach
	public void setup() {

		user = UserResponseDto.builder()
			.roleId(Role.ADMIN.getKey())
			.userId("user")
			.build();
		location = Location.builder()
			.locationId(1L)
			.locationName("location")
			.build();
		reserveItem = ReserveItem.builder()
			.reserveItemId(1L)
			.reserveItemName("test")
			.locationId(location.getLocationId())
			.location(location)
			.categoryId("place")
			.inventoryQty(100)
			.reserveMethodId("internet")
			.reserveMeansId("realtime")
			.requestStartDate(LocalDateTime.of(2021, 1, 1, 1, 1))
			.requestEndDate(LocalDateTime.of(2021, 12, 31, 23, 59))
			.operationStartDate(LocalDateTime.of(2021, 1, 1, 1, 1))
			.operationEndDate(LocalDateTime.of(2021, 12, 31, 23, 59))
			.build();

		reserve = Reserve.builder()
			.reserveId("1")
			.reserveItemId(reserveItem.getReserveItemId())
			.reserveQty(50)
			.reservePurposeContent("test")
			.reserveStatusId("request")
			.reserveStartDate(LocalDateTime.of(2021, 9, 9, 1, 1))
			.reserveEndDate(LocalDateTime.of(2021, 9, 20, 1, 1))
			.userId(user.getUserId())
			.userEmail("user@email.com")
			.userContactNo("contact")
			.build();
		reserve.setReserveItem(reserveItem);
		reserve.setUser(user);
	}

	@Test
	public void 예약신청관리_목록_조회_성공() throws Exception {
		BDDMockito.when(reserveRepository.search(ArgumentMatchers.any(ReserveRequestDto.class), ArgumentMatchers.any(
			Pageable.class)))
			.thenReturn(Flux.just(reserve));
		BDDMockito.when(reserveRepository.searchCount(ArgumentMatchers.any(ReserveRequestDto.class),
			ArgumentMatchers.any(Pageable.class)))
			.thenReturn(Mono.just(1L));

		webTestClient.get()
			.uri(API_URL + "?page=0&size=5")
			.exchange()
			.expectStatus().isOk()
			.expectBody(new ParameterizedTypeReference<RestResponsePage<ReserveListResponseDto>>() {
			})
			.value(page -> {
				assertThat(page.getTotalElements()).isEqualTo(1L);
				assertThat(page.getContent().get(0).getReserveId()).isEqualTo(reserve.getReserveId());
				page.getContent().stream().forEach(System.out::println);
			});

	}

	@Test
	@WithCustomMockUser(userId = "admin", role = Role.ADMIN)
	public void 관리자_취소_성공() throws Exception {
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.save(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve.updateReasonCancel("reason for cancellation")));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(true));

		webTestClient.put()
			.uri(API_URL + "/cancel/{reserveId}", reserve.getReserveId())
			.bodyValue(ReserveCancelRequestDto.builder().reasonCancelContent("reason for cancellation").build())
			.exchange()
			.expectStatus().isNoContent()
		;
	}

	@Test
	@WithCustomMockUser(userId = "user", role = Role.USER)
	public void 사용자_취소_성공() {
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.save(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve.updateReasonCancel("reason for cancellation")));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(true));

		webTestClient.put()
			.uri(API_URL + "/cancel/{reserveId}", reserve.getReserveId())
			.bodyValue(ReserveCancelRequestDto.builder().reasonCancelContent("reason for cancellation").build())
			.exchange()
			.expectStatus().isNoContent()
		;
	}

	@Test
	@WithCustomMockUser(userId = "test", role = Role.USER)
	public void 다른사용자_예약_취소_실패() throws Exception {
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.save(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(true));

		webTestClient.put()
			.uri(API_URL + "/cancel/{reserveId}", reserve.getReserveId())
			.bodyValue(ReserveCancelRequestDto.builder().reasonCancelContent("reason for cancellation").build())
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ErrorResponse.class)
			.value(response -> {
				assertThat(response.getMessage()).isEqualTo("해당 예약은 취소할 수 없습니다.");
				assertThat(response.getCode()).isEqualTo(ErrorCode.BUSINESS_CUSTOM_MESSAGE.getCode());
			});
	}

	@Test
	@WithCustomMockUser(userId = "user", role = Role.USER)
	public void 예약상태_완료_취소_실패() throws Exception {
		Reserve done = reserve.updateStatus(ReserveStatus.DONE.getKey());
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(done));
		BDDMockito.when(reserveRepository.save(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(done));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(true));

		webTestClient.put()
			.uri(API_URL + "/cancel/{reserveId}", reserve.getReserveId())
			.bodyValue(ReserveCancelRequestDto.builder().reasonCancelContent("reason for cancellation").build())
			.exchange()
			.expectBody(ErrorResponse.class)
			.value(response -> {
				assertThat(response.getMessage()).isEqualTo("해당 예약은 이미 실행되어 취소할 수 없습니다.");
				assertThat(response.getCode()).isEqualTo(ErrorCode.BUSINESS_CUSTOM_MESSAGE.getCode());
			});
		;
	}

	@Test
	@WithCustomMockUser(userId = "user", role = Role.USER)
	public void 관리자가_아닌_경우_승인_실패() throws Exception {
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.save(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveItemServiceClient.findById(ArgumentMatchers.anyLong()))
			.thenReturn(Mono.just(ReserveItemResponseDto.builder().reserveItem(reserveItem).build()));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(true));

		webTestClient.put()
			.uri(API_URL + "/approve/{reserveId}", reserve.getReserveId())
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ErrorResponse.class)
			.value(response -> {
				assertThat(response.getMessage()).isEqualTo("관리자만 승인할 수 있습니다.");
			});
	}

	@Test
	@WithCustomMockUser(userId = "admin", role = Role.ADMIN)
	public void 예약승인_성공() throws Exception {
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.save(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveItemServiceClient.findById(ArgumentMatchers.anyLong()))
			.thenReturn(Mono.just(ReserveItemResponseDto.builder().reserveItem(reserveItem).build()));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(true));

		webTestClient.put()
			.uri(API_URL + "/approve/{reserveId}", reserve.getReserveId())
			.exchange()
			.expectStatus().isNoContent();

	}

	@Test
	@WithCustomMockUser(userId = "admin", role = Role.ADMIN)
	public void 예약승인_실패_재고부족() throws Exception {
		ReserveItem failReserveItem = ReserveItem.builder()
			.reserveItemId(1L)
			.reserveItemName("test")
			.locationId(location.getLocationId())
			.location(location)
			.categoryId("equipment")
			.totalQty(20)
			.inventoryQty(10)
			.reserveMethodId("internet")
			.reserveMeansId("realtime")
			.isPeriod(false)
			.requestStartDate(LocalDateTime.of(2021, 1, 1, 1, 1))
			.requestEndDate(LocalDateTime.of(2021, 12, 31, 23, 59))
			.operationStartDate(LocalDateTime.of(2021, 1, 1, 1, 1))
			.operationEndDate(LocalDateTime.of(2021, 12, 31, 23, 59))
			.build();

		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.save(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.findAllByReserveDateWithoutSelf(
			ArgumentMatchers.anyString(), ArgumentMatchers.anyLong(),
			ArgumentMatchers.any(LocalDateTime.class), ArgumentMatchers.any(LocalDateTime.class)
		)).thenReturn(Flux.just(reserve));
		BDDMockito.when(reserveItemServiceClient.findById(ArgumentMatchers.anyLong()))
			.thenReturn(Mono.just(ReserveItemResponseDto.builder().reserveItem(failReserveItem).build()));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(false));

		webTestClient.put()
			.uri(API_URL + "/approve/{reserveId}", reserve.getReserveId())
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ErrorResponse.class)
			.value(response -> {
				assertThat(response.getMessage()).isEqualTo("해당 날짜에 예약할 수 있는 재고수량이 없습니다.");
			});

	}

	@Test
	@WithCustomMockUser(userId = "admin", role = Role.ADMIN)
	public void 관리자_예약정보_수정_성공() throws Exception {
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.save(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveItemServiceClient.findById(ArgumentMatchers.anyLong()))
			.thenReturn(Mono.just(ReserveItemResponseDto.builder().reserveItem(reserveItem).build()));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(true));

		ReserveUpdateRequestDto updateRequestDto =
			ReserveUpdateRequestDto.builder()
				.reserveItemId(reserve.getReserveItemId())
				.categoryId(reserve.getReserveItem().getCategoryId())
				.reservePurposeContent("purpose")
				.reserveQty(10)
				.reserveStartDate(reserve.getReserveStartDate())
				.reserveEndDate(reserve.getReserveEndDate())
				.attachmentCode(reserve.getAttachmentCode())
				.userId(reserve.getUserId())
				.userContactNo("contact update")
				.userEmail(reserve.getUserEmail())
				.build();

		webTestClient.put()
			.uri(API_URL + "/{reserveId}", reserve.getReserveId())
			.bodyValue(updateRequestDto)
			.exchange()
			.expectStatus().isNoContent()
		;
	}

	@Test
	@WithCustomMockUser(userId = "test", role = Role.USER)
	public void 다른사용자_예약정보_수정_실패() throws Exception {
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.save(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveItemServiceClient.findById(ArgumentMatchers.anyLong()))
			.thenReturn(Mono.just(ReserveItemResponseDto.builder().reserveItem(reserveItem).build()));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(true));

		ReserveUpdateRequestDto updateRequestDto =
			ReserveUpdateRequestDto.builder()
				.reserveItemId(reserve.getReserveItemId())
				.categoryId(reserve.getReserveItem().getCategoryId())
				.reservePurposeContent("purpose")
				.reserveQty(10)
				.reserveStartDate(reserve.getReserveStartDate())
				.reserveEndDate(reserve.getReserveEndDate())
				.attachmentCode(reserve.getAttachmentCode())
				.userId(reserve.getUserId())
				.userContactNo("contact update")
				.userEmail(reserve.getUserEmail())
				.build();

		webTestClient.put()
			.uri(API_URL + "/{reserveId}", reserve.getReserveId())
			.bodyValue(updateRequestDto)
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ErrorResponse.class)
			.value(response -> {
				assertThat(response.getMessage()).isEqualTo("해당 예약은 수정할 수 없습니다.");
			});
	}

	@Test
	@WithCustomMockUser(userId = "user", role = Role.USER)
	public void 사용자_예약정보_수정_성공() throws Exception {
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.save(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveItemServiceClient.findById(ArgumentMatchers.anyLong()))
			.thenReturn(Mono.just(ReserveItemResponseDto.builder().reserveItem(reserveItem).build()));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(true));

		ReserveUpdateRequestDto updateRequestDto =
			ReserveUpdateRequestDto.builder()
				.reserveItemId(reserve.getReserveItemId())
				.categoryId(reserve.getReserveItem().getCategoryId())
				.reservePurposeContent("purpose")
				.reserveQty(10)
				.reserveStartDate(reserve.getReserveStartDate())
				.reserveEndDate(reserve.getReserveEndDate())
				.attachmentCode(reserve.getAttachmentCode())
				.userId(reserve.getUserId())
				.userContactNo("contact update")
				.userEmail(reserve.getUserEmail())
				.build();

		webTestClient.put()
			.uri(API_URL + "/{reserveId}", reserve.getReserveId())
			.bodyValue(updateRequestDto)
			.exchange()
			.expectStatus().isNoContent()
		;

	}

	@Test
	@WithCustomMockUser(userId = "user", role = Role.USER)
	public void 사용자_상태승인인예약정보_수정_실패() throws Exception {
		Reserve failedReserve = reserve.withReserveStatusId(ReserveStatus.APPROVE.getKey());
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(failedReserve));
		BDDMockito.when(reserveRepository.save(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(failedReserve));
		BDDMockito.when(reserveItemServiceClient.findById(ArgumentMatchers.anyLong()))
			.thenReturn(Mono.just(ReserveItemResponseDto.builder().reserveItem(reserveItem).build()));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(false));

		ReserveUpdateRequestDto updateRequestDto =
			ReserveUpdateRequestDto.builder()
				.reserveItemId(reserve.getReserveItemId())
				.categoryId(reserve.getReserveItem().getCategoryId())
				.reservePurposeContent("purpose")
				.reserveQty(10)
				.reserveStartDate(reserve.getReserveStartDate())
				.reserveEndDate(reserve.getReserveEndDate())
				.attachmentCode(reserve.getAttachmentCode())
				.userId(reserve.getUserId())
				.userContactNo("contact update")
				.userEmail(reserve.getUserEmail())
				.build();

		webTestClient.put()
			.uri(API_URL + "/{reserveId}", reserve.getReserveId())
			.bodyValue(updateRequestDto)
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ErrorResponse.class)
			.value(response -> {
				assertThat(response.getMessage()).isEqualTo("예약 신청 상태인 경우에만 수정 가능합니다.");
			});

	}

	@Test
	public void 관리자_예약_성공() throws Exception {
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.insert(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveItemServiceClient.findById(ArgumentMatchers.anyLong()))
			.thenReturn(Mono.just(ReserveItemResponseDto.builder().reserveItem(reserveItem).build()));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(true));
		BDDMockito.when(reserveRepository.loadRelations(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve));

		ReserveSaveRequestDto saveRequestDto =
			ReserveSaveRequestDto.builder()
				.reserveItemId(reserve.getReserveItemId())
				.categoryId(reserve.getReserveItem().getCategoryId())
				.reservePurposeContent(reserve.getReservePurposeContent())
				.reserveQty(reserve.getReserveQty())
				.reserveStartDate(reserve.getReserveStartDate())
				.reserveEndDate(reserve.getReserveEndDate())
				.attachmentCode(reserve.getAttachmentCode())
				.userId(reserve.getUserId())
				.userContactNo(reserve.getUserContactNo())
				.userEmail(reserve.getUserEmail())
				.build();

		webTestClient.post()
			.uri(API_URL)
			.bodyValue(saveRequestDto)
			.exchange()
			.expectStatus().isCreated()
			.expectBody(ReserveResponseDto.class)
			.value(reserveResponseDto -> {
				System.out.println(reserveResponseDto);
			})
		;

	}

	@Test
	public void 예약신청_valid_실패() throws Exception {
		ReserveItem validReserveItem = ReserveItem.builder()
			.reserveItemId(1L)
			.reserveItemName("test")
			.locationId(location.getLocationId())
			.location(location)
			.categoryId("equipment")
			.totalQty(100)
			.inventoryQty(10)
			.operationStartDate(LocalDateTime.of(2021, 10, 1, 1, 1))
			.operationEndDate(LocalDateTime.of(2021, 10, 31, 23, 59))
			.build();
		reserve.setReserveItem(validReserveItem);
		BDDMockito.when(reserveRepository.findById(ArgumentMatchers.anyString()))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveRepository.insert(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve));
		BDDMockito.when(reserveItemServiceClient.findById(ArgumentMatchers.anyLong()))
			.thenReturn(Mono.just(ReserveItemResponseDto.builder().reserveItem(validReserveItem).build()));
		BDDMockito.when(reserveItemServiceClient.updateInventory(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
			.thenReturn(Mono.just(false));
		BDDMockito.when(reserveRepository.loadRelations(ArgumentMatchers.any(Reserve.class)))
			.thenReturn(Mono.just(reserve));

		ReserveSaveRequestDto saveRequestDto =
			ReserveSaveRequestDto.builder()
				.reserveItemId(reserve.getReserveItemId())
				.categoryId(reserve.getReserveItem().getCategoryId())
				.reservePurposeContent(reserve.getReservePurposeContent())
				.reserveQty(null)
				.reserveStartDate(LocalDateTime.of(2021, 11, 1, 1, 1))
				.reserveEndDate(reserve.getReserveEndDate())
				.attachmentCode(reserve.getAttachmentCode())
				.userId(reserve.getUserId())
				.userContactNo(reserve.getUserContactNo())
				.userEmail(reserve.getUserEmail())
				.build();

		webTestClient.post()
			.uri(API_URL)
			.bodyValue(saveRequestDto)
			.exchange()
			.expectStatus().isBadRequest()
		;
	}
}