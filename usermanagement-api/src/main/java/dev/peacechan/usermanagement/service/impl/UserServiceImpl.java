package dev.peacechan.usermanagement.service.impl;

import dev.peacechan.usermanagement.domain.User;
import dev.peacechan.usermanagement.domain.UserPrincipal;
import dev.peacechan.usermanagement.enumeration.Role;
import dev.peacechan.usermanagement.exception.domain.*;
import dev.peacechan.usermanagement.repository.UserRepository;

import dev.peacechan.usermanagement.service.EmailService;
import dev.peacechan.usermanagement.service.LoginAttemptService;
import dev.peacechan.usermanagement.service.UserService;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.mail.MessagingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static dev.peacechan.usermanagement.constant.FileConstant.*;
import static dev.peacechan.usermanagement.constant.UserImplConstant.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
@Service
@Transactional
@Qualifier("userDetailsService")
public class UserServiceImpl implements UserService, UserDetailsService {

        private UserRepository userRepository;
        private Logger LOGGER = LoggerFactory.getLogger(getClass());
        private PasswordEncoder passwordEncoder;
        private LoginAttemptService loginAttemptService;
        private EmailService emailService;

        public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, LoginAttemptService loginAttemptService, EmailService emailService) {
            this.userRepository = userRepository;
            this.passwordEncoder = passwordEncoder;
            this.loginAttemptService = loginAttemptService;
            this.emailService = emailService;
        }

        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
            User user = userRepository.findUserByUsername(username);
            if(user == null){
                throw new UsernameNotFoundException(USER_NOT_FOUND_BY_USERNAME + username);
            }else{
                try {
                    validateLoginAttempt(user);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                user.setLastLoginDateDisplay(user.getLastLoginDate());
                user.setLastLoginDate(new Date());
                userRepository.save(user);
                UserPrincipal userPrincipal = new UserPrincipal(user);
                return userPrincipal;
            }
        }

        @Override
        public User register(String firstName, String lastName, String username, String email) throws UserNotFoundException, UsernameExistException, EmailExistException, MessagingException {
            validateNewUserAndEmail(StringUtils.EMPTY, username, email);
            User user = new User();
            String password = generatePassword();
            user.setUserId(generateUserId());
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setUsername(username);
            user.setEmail(email);
            user.setJoinDate(new Date());
            user.setNotLocked(true);
            user.setActive(true);
            user.setPassword(encodePassword(password));
            user.setRole(Role.ROLE_USER.name());
            user.setAuthorities(Role.ROLE_USER.getAuthorities());
            user.setProfileImageUrl(getTemporarilyProfileImageUrl(username));
            this.userRepository.save(user);
            LOGGER.info("New user password => " + password);
            this.emailService.sendEmail(firstName, password, email);
            return user;
        }

        public String generateUserId() {
            return RandomStringUtils.randomNumeric(10);
        }

        @Override
        public List<User> getUsers() {
            return userRepository.findAll();
        }

        @Override
        public User findUserByUsername(String username) {
            return userRepository.findUserByUsername(username);
        }

        @Override
        public User findUserByEmail(String email) {
            return userRepository.findUserByEmail(email);
        }

        @Override
        public User addNewUser(String firstName, String lastName, String username, String email, String role, boolean isNonLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException, NotAnImageFileException {
            validateNewUserAndEmail(StringUtils.EMPTY, username, email);
            User user = new User();
            String password = generatePassword();
            user.setUserId(generateUserId());
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setUsername(username);
            user.setEmail(email);
            user.setNotLocked(isNonLocked);
            user.setActive(isActive);
            user.setPassword(encodePassword(password));
            user.setRole(getRoleEnumName(role).name());
            user.setAuthorities(getRoleEnumName(role).getAuthorities());
            user.setJoinDate(new Date());
            this.userRepository.save(user);
            saveProfileImage(user, profileImage);
            return user;
        }

        @Override
        public User updateUser(String currentUsername, String newFirstName, String newLastName, String newUsername, String newEmail, String role, boolean isNonLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException, NotAnImageFileException {
            User currentUser = validateNewUserAndEmail(currentUsername, newUsername, newEmail);
            currentUser.setFirstName(newFirstName);
            currentUser.setLastName(newLastName);
            currentUser.setUsername(newUsername);
            currentUser.setEmail(newEmail);
            currentUser.setNotLocked(isNonLocked);
            currentUser.setActive(isActive);
            currentUser.setRole(getRoleEnumName(role).name());
            currentUser.setAuthorities(getRoleEnumName(role).getAuthorities());
            this.userRepository.save(currentUser);
            saveProfileImage(currentUser, profileImage);
            return currentUser;
        }

        @Override
        public void deleteUser(Long id) {
            this.userRepository.deleteById(id);
        }

        @Override
        public void resetPassword(String email) throws EmailNotFoundException, MessagingException {
            User user = this.userRepository.findUserByEmail(email);
            if(user == null){
                throw new EmailNotFoundException(USER_NOT_FOUND_BY_EMAIL);
            }
            String password = generatePassword();
            user.setPassword(encodePassword(password));
            userRepository.save(user);
            this.emailService.sendEmail(user.getFirstName(), password, email);
        }

        @Override
        public User updateProfileImage(String username, MultipartFile profileImage) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException, NotAnImageFileException {
            User user = validateNewUserAndEmail(username, null, null);
            saveProfileImage(user, profileImage);
            return null;
        }

        private void saveProfileImage(User user, MultipartFile profileImage) throws IOException, NotAnImageFileException {
            if(profileImage != null){
                if(!Arrays.asList(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_GIF_VALUE, MediaType.IMAGE_PNG_VALUE).contains(profileImage.getContentType())){
                    throw new NotAnImageFileException(profileImage.getOriginalFilename() + " not a image file. Please upload the correct one.");
                }
                Path userFolder = Paths.get(USER_FOLDER + user.getUsername()).toAbsolutePath().normalize();
                if(!Files.exists(userFolder)){
                    Files.createDirectories(userFolder);
                    LOGGER.info(DIRECTORY_CREATED);
                }
                Files.deleteIfExists(Paths.get(userFolder + user.getUsername() + DOT + JPG_EXTENSION));
                Files.copy(
                        profileImage.getInputStream(),
                        userFolder.resolve(user.getUsername() + DOT + JPG_EXTENSION),
                        REPLACE_EXISTING
                );
                user.setProfileImageUrl(setProfileImageUrl(user.getUsername()));
                userRepository.save(user);
                LOGGER.info(FILE_SAVED_IN_FILE_SYSTEM + profileImage.getOriginalFilename());
            }
        }

        private String setProfileImageUrl(String username) {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path(USER_IMAGE_PATH + username + FORWARD_SLASH + username + DOT + JPG_EXTENSION)
                    .toUriString();
        }

        private Role getRoleEnumName(String role) {
            return Role.valueOf(role.toUpperCase());
        }

        private User validateNewUserAndEmail(String currentUsername, String newUsername, String newEmail) throws UserNotFoundException, UsernameExistException, EmailExistException {
            User userByUsername = findUserByUsername(newUsername);
            User userByEmail = findUserByEmail(newEmail);

            if(StringUtils.isNotBlank(currentUsername)){
                User currentUser = findUserByUsername(currentUsername);
                if (currentUser == null){
                    throw new UserNotFoundException(USER_NOT_FOUND_BY_USERNAME + currentUsername);
                }
                if(userByUsername != null && !currentUser.getId().equals(userByUsername.getId())){
                    throw new UsernameExistException(USERNAME_ALREADY_EXIST);
                }
                if(userByEmail != null && !currentUser.getId().equals(userByEmail.getId())){
                    throw new EmailExistException(EMAIL_ALREADY_EXIST);
                }
                return currentUser;
            }else{
                if(userByUsername != null){
                    throw new UsernameExistException(USERNAME_ALREADY_EXIST);
                }
                if(userByEmail != null){
                    throw new EmailExistException(EMAIL_ALREADY_EXIST);
                }
                return null;
            }
        }

        private void validateLoginAttempt(User user) throws ExecutionException {
            if(user.isNotLocked()){
                if(this.loginAttemptService.hasExceededMaxAttempts(user.getUsername())){
                    user.setNotLocked(false);
                }else{
                    user.setNotLocked(true);
                }
            }else{
                loginAttemptService.evictUserFromLoginAttemptCache(user.getUsername());
            }
        }

        private String getTemporarilyProfileImageUrl(String username) {
            return ServletUriComponentsBuilder.fromCurrentContextPath().path(DEFAULT_USER_IMAGE_PATH + username).toUriString();
        }

        private String encodePassword(String password) {
            return this.passwordEncoder.encode(password);
        }

        private String generatePassword() {
            return RandomStringUtils.randomAlphanumeric(10);
        }
    }