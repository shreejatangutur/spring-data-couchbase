/*
 * Copyright 2012-2022 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.couchbase.domain;

import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.couchbase.core.mapping.Document;

@Document
/**
 * @author Michael Reiche
 */
@TypeAlias("org.springframework.data.couchbase.domain.Airline") // needed for join to fetch Airline documents
public class AirlineWithHomeAirport extends Airline {

	Airport homeAirport;

	@PersistenceConstructor
	public AirlineWithHomeAirport(String id, String name, String hqCountry, Airport homeAirport) {
		super(id, name, hqCountry);
		this.homeAirport = homeAirport;
	}

	public void setHomeAirport(Airport homeAirport) {
		this.homeAirport = homeAirport;
	}
}
