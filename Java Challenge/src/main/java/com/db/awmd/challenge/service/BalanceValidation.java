package com.db.awmd.challenge.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
public class BalanceValidation {

	/**
	 * 
	 * @param transferAmount
	 * @param bal
	 * @return
	 * 
	 * 
	 */
	public boolean validateTransferAmoount(BigDecimal transferAmount, /** It shgould be fetched from db**/BigDecimal bal){
		
		return transferAmount.compareTo(BigDecimal.ZERO) > 0  && checkAvailableBalance(transferAmount, bal);
	}
	
	/**
	 * 
	 * @param transferAmount
	 * @param bal
	 * @return
	 */
	private boolean checkAvailableBalance(BigDecimal transferAmount, /** It shgould be fetched from db**/BigDecimal bal){
		return bal.compareTo(BigDecimal.ZERO) > 0  && (bal.subtract(transferAmount).compareTo(BigDecimal.ZERO)) > 0;
	}
	
	
} 