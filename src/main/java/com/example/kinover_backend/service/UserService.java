package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 유저 아이디 통해서 유저 조회 (DTO 반환)
    @Transactional
    public UserDTO getUserById(Long userId) {
        Optional<User> user = userRepository.findByUserId(userId);
        if (user.isPresent()) {
            return new UserDTO(user);
        }
        return new UserDTO(user);  // User 엔티티를 UserDTO로 변환해서 반환
    }

    // 유저 추가
    public UserDTO addUser(User user) {
        User savedUser = userRepository.save(user);
        return new UserDTO(savedUser);  // 저장된 User 엔티티를 DTO로 변환해서 반환
    }

    // 유저 삭제
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    // 유저 프로필 수정
    public UserDTO modifyUser(User updatedUser) {
            // 기존 유저 조회
        if (updatedUser.getUserId() == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        User existingUser = userRepository.findById(updatedUser.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 필드 업데이트
        existingUser.setName(updatedUser.getName());
        existingUser.setBirth(updatedUser.getBirth());
        existingUser.setImage(updatedUser.getImage());

        UserDTO userDto=new UserDTO(userRepository.save(existingUser));
        return userDto;
    }
}
