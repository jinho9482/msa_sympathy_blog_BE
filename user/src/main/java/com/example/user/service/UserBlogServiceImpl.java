
package com.example.user.service;

import com.example.user.dto.request.UserBlogRequest;
import com.example.user.dto.response.UserBlogResponse;
import com.example.user.global.domain.entity.UserBlog;
import com.example.user.global.domain.repository.UserBlogRepository;
import com.example.user.kafka.dto.KafkaUserBlogDto;
import com.example.user.global.dto.UserBlogDto;
import com.example.user.global.utils.JwtUtil;
import com.example.user.kafka.dto.KafkaStatus;
import com.example.user.kafka.producer.UserBlogIdProducer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class UserBlogServiceImpl implements UserBlogService, UserDetailsService {
    private final UserBlogIdProducer userBlogIdProducer;
    public final UserBlogRepository userRepository;
    private final JwtUtil jwtUtil;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(email + " not found"));
    }


    @Override
    public UserBlogDto saveInfo(UserBlogDto req) {
        // 데이터베이스에 저장할 Entity 생성
        UserBlog userBlog = UserBlog.builder()
                .email(req.toEntity().getEmail())
                .nickname(req.toEntity().getNickname())
                .id(req.toEntity().getId())
                .build();

        // 데이터베이스에 저장
        UserBlog save = userRepository.save(userBlog);
        return UserBlogDto.from(save);
    }

    @Override
    public UserBlog update(UserBlogRequest req, UUID id) {
        UserBlog userBlog = userRepository.findById(id).orElseThrow(
                EntityNotFoundException::new);
        userBlog.setNickname(req.nickname());
        userBlog.setBlogName(req.blogName());
        userRepository.save(userBlog);

        KafkaUserBlogDto kafkaUserBlogDto = new KafkaUserBlogDto(id.toString(),req.nickname());
        KafkaStatus<KafkaUserBlogDto> kafkaStatus = new KafkaStatus<>(kafkaUserBlogDto,"update");
        userBlogIdProducer.send(kafkaUserBlogDto,"update");
        return userBlog;
    }

    @Override
    public KafkaUserBlogDto deleteUserBlog(UserBlogRequest req, UUID id) {

        UserBlog userBlog = userRepository.findById(id).orElseThrow(
                EntityNotFoundException::new);

        userRepository.delete(userBlog);

        KafkaUserBlogDto kafkaUserBlogDto = new KafkaUserBlogDto(id.toString(),null);
        KafkaStatus<KafkaUserBlogDto> kafkaStatus = new KafkaStatus<>(kafkaUserBlogDto,"delete");
        userBlogIdProducer.send(kafkaUserBlogDto,"delete");

        return kafkaUserBlogDto;
    }


    public UserBlogResponse getUserBlogById(UUID id) {
        UserBlogResponse blogResponse = UserBlogResponse
                .from(userRepository.findAllById(id)
                        .orElseThrow(EntityNotFoundException::new));
        return blogResponse;
    }

}
