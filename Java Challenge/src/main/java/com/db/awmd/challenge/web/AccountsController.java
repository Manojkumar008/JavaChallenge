package com.db.awmd.challenge.web;

import java.math.BigDecimal;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.DuplicateAccountIdException;
import com.db.awmd.challenge.service.AccountsService;
import com.db.awmd.challenge.service.EmailNotificationService;
import com.db.awmd.dto.TransferDetails;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/v1/accounts")
@Slf4j
public class AccountsController {

	@Autowired
	private final AccountsService accountsService;
	private static final Logger logger = LoggerFactory.getLogger(AccountsController.class);
	private Lock transferLock;

	@Autowired
	private EmailNotificationService emailNotificationService;

	@Autowired
	public AccountsController(AccountsService accountsService) {
		this.accountsService = accountsService;
		transferLock = new ReentrantLock();
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> createAccount(@RequestBody @Valid Account account) {
		logger.info("Creating account {}", account);

		try {
			this.accountsService.createAccount(account);
		} catch (DuplicateAccountIdException daie) {
			return new ResponseEntity<>(daie.getMessage(), HttpStatus.BAD_REQUEST);
		}

		return new ResponseEntity<>(HttpStatus.CREATED);
	}

	@GetMapping(path = "/{accountId}")
	public Account getAccount(@PathVariable String accountId) {
		logger.info("Retrieving account for id {}", accountId);
		return this.accountsService.getAccount(accountId);
	}

	/**
	 * This method is used for transferring the amount between two account.
	 * 
	 * Lock of java.util.concurrent.Lock provide more extensive locking operations
	 * than can be obtained using synchronized methods and statements. They allow
	 * more flexible structuring, may have quite different properties, and may
	 * support multiple associated Condition objects. A lock is a tool for
	 * controlling access to a shared resource by multiple threads. Commonly, a lock
	 * provides exclusive access to a shared resource: only one thread at a time can
	 * acquire the lock and all access to the shared resource requires that the lock
	 * be acquired first. However, some locks may allow concurrent access to a
	 * shared resource, such as the read lock of a ReadWriteLock.
	 * 
	 * @param transferDetails
	 * @return
	 */
	@PutMapping(path = "/transferAmount", consumes = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody ResponseEntity<Object> transferAmount(@RequestBody @Valid TransferDetails transferDetails) {

		logger.info("Creating account {}", transferDetails);

		// to get the details of from account
		Account accountFrom = accountsService.getAccount(transferDetails.getAccountFrom());

		// to get the details of To account
		Account accountTo = accountsService.getAccount(transferDetails.getAccountTo());

		// validating the amount, so that it will not get overdraft facility
		if (validateTransferAmoount(transferDetails.getAmount(), accountFrom.getBalance())) {

			// To transfer the amount between two account
			transferAmount(transferDetails, accountFrom, accountTo);

			// Thread perform the actioned of transfering the amount between account.
			String threadName = Thread.currentThread().getName();
			logger.info("-----thread name--------------" + threadName);

		} else {
			return new ResponseEntity<>("Insufficient Balance : Due To Insufficient Balance not able to transfer amount"
					+ transferDetails.getAmount(), HttpStatus.BAD_REQUEST);
		}
		return new ResponseEntity<>("Amount successfully transferred to beneficiary Account", HttpStatus.OK);
	}

	private ResponseEntity<String> transferAmount(TransferDetails transferDetails, Account accountFrom,
			Account accountTo) {
		try {
			// locking the thread so that other cannot interrupt the processing.
			transferLock.lock();

			// deducting the amount from fromAccount
			accountFrom.setBalance(accountFrom.getBalance().subtract(transferDetails.getAmount()));

			// adding the deducted the amount to toAccount
			accountTo.setBalance(accountTo.getBalance().add(transferDetails.getAmount()));

			// Committing the changes to toAccount
			accountsService.updateAccount(accountTo);

			// Committing the changes to fromAcoount
			accountsService.updateAccount(accountFrom);

			// Sending the notification to sender and receiver about credit and debit
			emailNotificationService.notifyAboutTransfer(accountFrom,
					" is debited with amount " + transferDetails.getAmount());

			emailNotificationService.notifyAboutTransfer(accountTo,
					" is credited with amount " + transferDetails.getAmount());
		} catch (Exception e) {
			return new ResponseEntity<>("not able to Transfered the money", HttpStatus.BAD_REQUEST);

		}

		finally {
			//releasing the lock 
			transferLock.unlock();
		}
		return new ResponseEntity<>("Transfered money succesfully..", HttpStatus.BAD_REQUEST);

	}

	/**
	 * 
	 * @param transferAmount
	 * @param bal
	 * @return
	 * 
	 * 
	 */
	public boolean validateTransferAmoount(BigDecimal transferAmount, BigDecimal bal) {

		return transferAmount.compareTo(BigDecimal.ZERO) > 0 && checkAvailableBalance(transferAmount, bal);
	}

	/**
	 * 
	 * @param transferAmount
	 * @param bal
	 * @return
	 */
	private boolean checkAvailableBalance(BigDecimal transferAmount, BigDecimal bal) {
		return bal.compareTo(BigDecimal.ZERO) > 0 && (bal.subtract(transferAmount).compareTo(BigDecimal.ZERO)) >= 0;
	}

}
