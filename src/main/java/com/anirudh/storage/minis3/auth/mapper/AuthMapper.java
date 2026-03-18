package com.anirudh.storage.minis3.auth.mapper;

import com.anirudh.storage.minis3.auth.dto.LoginResponse;
import com.anirudh.storage.minis3.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

/**
 * @author Anirudh
 * @since 22/02/26
 */
@Component
@RequiredArgsConstructor
public class AuthMapper {
    private final ModelMapper modelMapper;

    public LoginResponse toLoginResponse(User user){
       return modelMapper.map(user,LoginResponse.class);
    }
}
