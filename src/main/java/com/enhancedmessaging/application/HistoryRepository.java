package com.enhancedmessaging.application;

import com.enhancedmessaging.domain.PrivateMessage;
import java.io.IOException;
import java.util.List;

public interface HistoryRepository
{
	List<PrivateMessage> load(String accountKey) throws IOException;

	void save(String accountKey, List<PrivateMessage> messages) throws IOException;

	void delete(String accountKey) throws IOException;
}
