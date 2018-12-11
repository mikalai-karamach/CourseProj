package by.bsuir.karamach.serviceworker.logic.impl;

import by.bsuir.karamach.serviceworker.entity.AccessRole;
import by.bsuir.karamach.serviceworker.entity.Customer;
import by.bsuir.karamach.serviceworker.entity.RegistrationRequest;
import by.bsuir.karamach.serviceworker.logic.ServiceException;
import by.bsuir.karamach.serviceworker.logic.UserCreationService;
import by.bsuir.karamach.serviceworker.logic.validator.CustomerInfoValidator;
import by.bsuir.karamach.serviceworker.repository.CustomerRepository;
import by.bsuir.karamach.serviceworker.repository.RegistrationRequestRepository;
import by.bsuir.karamach.serviceworker.security.SecurityHelper;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class RegisterService implements UserCreationService {


    private static final String MESSAGE_TO_USER = "   Hello, %s! \n" +
            "Welcome to our platform, \n" +
            "To activate your account, use this code: \n" +
            "%s";
    private static final String ACCOUNT_ACTIVATION = "Account activation";

    private SecurityHelper securityHelper;

    private CustomerRepository customerRepository;

    private RegistrationRequestRepository requestRepository;


    private MailSender sender;

    public RegisterService(SecurityHelper securityHelper, CustomerRepository customerRepository, RegistrationRequestRepository requestRepository, MailSender sender) {
        this.securityHelper = securityHelper;
        this.customerRepository = customerRepository;
        this.requestRepository = requestRepository;
        this.sender = sender;
    }

    @Override
    public void activateUser(String code, String publicId) throws ServiceException {
        RegistrationRequest registrationRequest = requestRepository.findByActivationCode(code);

        if (registrationRequest != null) {
            boolean isRealUser = registrationRequest.getGeneratedPublicId().equals(publicId);

            if (isRealUser) {
                requestRepository.delete(registrationRequest);

                Customer customer = getCustomerFromRegistrationRequest(registrationRequest);

                customerRepository.save(customer);
            } else {
                throw new ServiceException("Illegal public key for this user");
            }
        } else {
            throw new ServiceException("No such user activation code");
        }
    }

    private Customer getCustomerFromRegistrationRequest(RegistrationRequest registrationRequest) {
        Customer customer = new Customer();

        customer.setEmail(registrationRequest.getEmail());
        customer.setHashedPass(registrationRequest.getHashedPass());

        customer.setFirstName(registrationRequest.getFirstName());
        customer.setLastName(registrationRequest.getLastName());
        customer.setBirthYear(registrationRequest.getBirthYear());
        customer.setFemale(registrationRequest.isFemale());


        customer.setRole(Collections.singleton(AccessRole.USER));
        return customer;
    }

    @Override
    public void createRegistrationRequest(RegistrationRequest registrationRequest) throws ServiceException {
        if (CustomerInfoValidator.isValidCustomerData(registrationRequest)) {

            String email = registrationRequest.getEmail();

            Customer alreadyRegisteredCustomer = customerRepository.findByEmail(email);
            RegistrationRequest alreadyExistingRequest = requestRepository.findByEmail(email);

            if ((alreadyRegisteredCustomer == null) && (alreadyExistingRequest == null)) {

                registrationRequest.setActivationCode(securityHelper.generateActivationCode());
                registrationRequest.setGeneratedPublicId(securityHelper.generatePublicId());

                requestRepository.save(registrationRequest);

                String message = String.format(MESSAGE_TO_USER,
                        registrationRequest.getFirstName(), registrationRequest.getActivationCode());

                sender.send(email, ACCOUNT_ACTIVATION, message);

            } else {
                throw new ServiceException("Email is already taken!");
            }
        } else {
            throw new ServiceException("Invalid data input!");
        }
    }
}
