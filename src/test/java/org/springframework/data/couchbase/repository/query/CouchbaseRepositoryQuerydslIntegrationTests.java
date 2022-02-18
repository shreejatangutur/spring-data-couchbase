/*
 * Copyright 2017-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.couchbase.repository.query;

import static com.couchbase.client.java.query.QueryScanConsistency.REQUEST_PLUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.data.couchbase.util.Util.comprises;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.core.CouchbaseOperations;
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.data.couchbase.core.mapping.event.ValidatingCouchbaseEventListener;
import org.springframework.data.couchbase.core.query.QueryCriteriaDefinition;
import org.springframework.data.couchbase.domain.Airline;
import org.springframework.data.couchbase.domain.AirlineRepository;
import org.springframework.data.couchbase.domain.AirlineWithHomeAirport;
import org.springframework.data.couchbase.domain.Airport;
import org.springframework.data.couchbase.domain.NaiveAuditorAware;
import org.springframework.data.couchbase.domain.QAirline;
import org.springframework.data.couchbase.domain.QAirlineWithHomeAirport;
import org.springframework.data.couchbase.domain.QAirport;
import org.springframework.data.couchbase.domain.time.AuditingDateTimeProvider;
import org.springframework.data.couchbase.repository.auditing.EnableCouchbaseAuditing;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;
import org.springframework.data.couchbase.repository.support.BasicQuery;
import org.springframework.data.couchbase.repository.support.SpringDataCouchbaseQuery;
import org.springframework.data.couchbase.repository.support.SpringDataCouchbaseSerializer;
import org.springframework.data.couchbase.util.Capabilities;
import org.springframework.data.couchbase.util.ClusterType;
import org.springframework.data.couchbase.util.IgnoreWhen;
import org.springframework.data.couchbase.util.JavaIntegrationTests;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.couchbase.client.java.env.ClusterEnvironment;
import com.couchbase.client.java.query.QueryScanConsistency;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.MapPath;

/**
 * Repository tests
 *
 * @author Michael Reiche
 */
@SpringJUnitConfig(CouchbaseRepositoryQuerydslIntegrationTests.Config.class)
@IgnoreWhen(missesCapabilities = Capabilities.QUERY, clusterTypes = ClusterType.MOCKED)
public class CouchbaseRepositoryQuerydslIntegrationTests extends JavaIntegrationTests {

	@Autowired AirlineRepository airlineRepository;

	static QAirline airline = QAirline.airline;
	// saved
	static Airline united = new Airline("1", "United Airlines", "US");
	static Airline lufthansa = new Airline("2", "Lufthansa", "DE");
	static Airline emptyStringAirline = new Airline("3", "Empty String Country", "");
	static Airline nullStringAirline = new Airline("4", "Null String Country", null);
	static Airline unitedLowercase = new Airline("5", "united airlines", "US");
	static Airline nameMissing = new Airline("6", null, "US");

	static Airline[] saved = new Airline[] { united, lufthansa, emptyStringAirline, nullStringAirline, unitedLowercase,
			nameMissing };
	static Airline[] containsName = new Airline[] { united, lufthansa, emptyStringAirline, nullStringAirline,
			unitedLowercase };
	// not saved
	static Airline flyByNight = new Airline("1001", "Fly By Night", "UK");
	static Airline sleepByDay = new Airline("1002", "Sleep By Day", "CA");
	static Airport ord = new Airport("airport:3", "ord", "ord", united.getId());
	static Airport den = new Airport("airport:4", "den", "den", united.getId());
	static Airport jfk = new Airport("airport:9", "jfk", "jfk", unitedLowercase.getId());
	static Airline[] notSaved = new Airline[] { flyByNight, sleepByDay };

	SpringDataCouchbaseSerializer serializer = new SpringDataCouchbaseSerializer(couchbaseTemplate.getConverter());

	@BeforeAll
	static public void beforeAll() {
		callSuperBeforeAll(new Object() {});
		ApplicationContext ac = new AnnotationConfigApplicationContext(
				CouchbaseRepositoryQuerydslIntegrationTests.Config.class);
		CouchbaseTemplate template = (CouchbaseTemplate) ac.getBean("couchbaseTemplate");
		for (Airline airline : saved) {
			template.insertById(Airline.class).one(airline);
		}
		template.findByQuery(Airline.class).withConsistency(REQUEST_PLUS).all();
		template.insertById(Airport.class).one(ord);
		template.insertById(Airport.class).one(den);
		template.insertById(Airport.class).one(jfk);
		template.findByQuery(Airport.class).withConsistency(REQUEST_PLUS).all();
	}

	@AfterAll
	static public void afterAll() {
		ApplicationContext ac = new AnnotationConfigApplicationContext(
				CouchbaseRepositoryQuerydslIntegrationTests.Config.class);
		CouchbaseTemplate template = (CouchbaseTemplate) ac.getBean("couchbaseTemplate");
		for (Airline airline : saved) {
			template.removeById(Airline.class).one(airline.getId());
		}
		template.findByQuery(Airline.class).withConsistency(REQUEST_PLUS).all();
		template.removeById(Airport.class).one(ord.getId());
		template.removeById(Airport.class).one(den.getId());
		template.removeById(Airport.class).one(jfk.getId());
		template.findByQuery(Airport.class).withConsistency(REQUEST_PLUS).all();
		callSuperAfterAll(new Object() {});
	}

	@Test
	void testEq() {
		{
			BooleanExpression predicate = airline.name.eq(flyByNight.getName());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result, Arrays.stream(containsName).filter(a -> equals(a, flyByNight)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name = $1", bq(predicate));

		}
		{
			BooleanExpression predicate = airline.name.eq(united.getName());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName).filter(a -> equals(a, united)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name = $1", bq(predicate));
		}
	}

	// this gives hqCountry == "" and hqCountry is missing
	// @Test
	void testStringIsEmpty() {
		{
			BooleanExpression predicate = airline.hqCountry.isEmpty();
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, emptyStringAirline, nullStringAirline), "[unexpected] -> [missing]");
			assertEquals(" WHERE UPPER(name) like $1", bq(predicate));
		}
	}

	@Test
	void testNot() {
		{
			BooleanExpression predicate = airline.name.eq(united.getName()).and(airline.hqCountry.eq(united.getHqCountry()))
					.not();
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertEquals(" WHERE not( (  (hqCountry = $1) and   (name = $2)) )", bq(predicate));
			assertNull(comprises(result,
					Arrays.stream(containsName)
							.filter(a -> !(united.getName().equals(a.getName()) && united.getHqCountry().equals(a.getHqCountry())))
							.toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
		}
		{
			BooleanExpression predicate = airline.name.in(Arrays.asList(united.getName())).not();
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertEquals(" WHERE not( (name = $1) )", bq(predicate));

			assertNull(comprises(result, Arrays.stream(containsName).filter(a -> !equals(a, united)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
		}
		{
			BooleanExpression predicate = airline.name.eq(united.getName()).not();
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName).filter(a -> !equals(a, united)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE not( (name = $1) )", bq(predicate));
		}
	}

	@Test
	void testAnd() {
		{
			BooleanExpression predicate = airline.name.eq(united.getName()).and(airline.hqCountry.eq(united.getHqCountry()));
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName)
					.filter(a -> equals(a, united) && a.getHqCountry().equals(united.getHqCountry())).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE   (name = $1) and   (hqCountry = $2)", bq(predicate));
		}
		{
			BooleanExpression predicate = airline.name.eq(united.getName())
					.and(airline.hqCountry.eq(lufthansa.getHqCountry()));
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName)
					.filter(a -> equals(a, united) && a.getHqCountry().equals(lufthansa.getHqCountry())).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE   (name = $1) and   (hqCountry = $2)", bq(predicate));
		}
	}

	@Test
	void testOr() {
		{
			BooleanExpression predicate = airline.name.eq(united.getName())
					.or(airline.hqCountry.eq(lufthansa.getHqCountry()));
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result,
					Arrays.stream(containsName).filter(a -> equals(a, united) || equals(a, lufthansa)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE   (name = $1) or   (hqCountry = $2)", bq(predicate));
		}
	}

	@Test
	void testNe() {
		{
			BooleanExpression predicate = airline.name.ne(united.getName());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName).filter(a -> !equals(a, united)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name != $1", bq(predicate));
		}
	}

	@Test
	void testStartsWith() {
		{
			BooleanExpression predicate = airline.name.startsWith(united.getName().substring(0, 5));
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName)
					.filter(a -> startsWith(a, united.getName().substring(0, 5))).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name like ($1||\"%\")", bq(predicate));
		}
	}

	@Test
	void testStartsWithIgnoreCase() {
		{
			BooleanExpression predicate = airline.name.startsWithIgnoreCase(united.getName().substring(0, 5));
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName)
					.filter(a -> startsWithIC(a, united.getName().substring(0, 5))).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE UPPER(name) like ($1||\"%\")", bq(predicate));
		}
	}

	@Test
	void testEndsWith() {
		{
			BooleanExpression predicate = airline.name.endsWith(united.getName().substring(1));
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result,
					Arrays.stream(containsName).filter(a -> endsWith(a, united.getName().substring(1))).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name like (\"%\"||$1)", bq(predicate));

		}
	}

	@Test
	void testEndsWithIgnoreCase() {
		{
			BooleanExpression predicate = airline.name.endsWithIgnoreCase(united.getName().substring(1));
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName).filter(a -> endsWithIC(a, united.getName().substring(1)))
					.toArray(Airline[]::new)), "[unexpected] -> [missing]");
			assertEquals(" WHERE UPPER(name) like (\"%\"||$1)", bq(predicate));
		}
	}

	@Test
	void testEqIgnoreCase() {
		{
			BooleanExpression predicate = airline.name.equalsIgnoreCase(flyByNight.getName());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result, Arrays.stream(containsName).filter(a -> equalsIC(a, flyByNight)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE UPPER(name) = $1", bq(predicate));
		}
		{
			BooleanExpression predicate = airline.name.equalsIgnoreCase(united.getName());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result, Arrays.stream(containsName).filter(a -> equalsIC(a, united)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE UPPER(name) = $1", bq(predicate));
		}

	}

	@Test
	void testContains() {
		{
			BooleanExpression predicate = airline.name.contains("United");
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result, Arrays.stream(containsName).filter(a -> contains(a, "United")).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE contains(name, $1)", bq(predicate));
		}
	}

	@Test
	void testContainsIgnoreCase() {
		{
			BooleanExpression predicate = airline.name.containsIgnoreCase("united");
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result, Arrays.stream(containsName).filter(a -> containsIC(a, "united")).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE contains(UPPER(name), $1)", bq(predicate));

		}
	}

	@Test
	void testMatches() {
		{
			BooleanExpression predicate = airline.name.matches("[Uu]nited.*");
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result, Arrays.stream(containsName).filter(a -> matches(a, "[Uu]nited.*")).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE regexp_like(name, $1)", bq(predicate));
		}
	}

	@Test
	void testLike() {
		{
			BooleanExpression predicate = airline.name.like("%nited%");
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result, Arrays.stream(containsName).filter(a -> contains(a, "nited")).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name like $1", bq(predicate));
		}
	}

	@Test
	void testLikeIgnoreCase() {
		{
			BooleanExpression predicate = airline.name.likeIgnoreCase("%Airlines");
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result, Arrays.stream(containsName).filter(a -> endsWithIC(a, "Airlines")).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE UPPER(name) like $1", bq(predicate));
		}
	}

	// This is 'between' is inclusive
	@Test
	void testBetween() {
		{
			BooleanExpression predicate = airline.name.between(flyByNight.getName(), united.getName());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result, Arrays.stream(containsName)
							.filter(a -> compareTo(a, flyByNight) >= 0 && compareTo(a, united) <= 0).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name between $1 and $2", bq(predicate));
		}
	}

	@Test
	void testIn() {
		{
			BooleanExpression predicate = airline.name.in(Arrays.asList(united.getName()));
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName).filter(a -> equals(a, united)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name = $1", bq(predicate));
		}

		{
			BooleanExpression predicate = airline.name.in(Arrays.asList(united.getName(), lufthansa.getName()));
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertEquals(" WHERE name in $1", bq(predicate));
			assertNull(comprises(result,
					Arrays.stream(containsName).filter(a -> equals(a, united) || equals(a, lufthansa)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
		}

		{
			BooleanExpression predicate = airline.name.in("Fly By Night", "Sleep By Day");
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName)
					.filter(a -> equals(a, flyByNight) || equals(a, sleepByDay)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name in $1", bq(predicate));
		}
	}

	@Test
	void testNotIn() {
		{
			BooleanExpression predicate = airline.name.notIn("Fly By Night", "Sleep By Day");
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName)
					.filter(a -> !(equals(a, flyByNight) || equals(a, sleepByDay))).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE not( (name in $1) )", bq(predicate));
		}
		{
			BooleanExpression predicate = airline.name.notIn(united.getName());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(containsName).filter(a -> !equals(a, united)).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name != $1", bq(predicate));
		}
	}

	@Test
	void testLt() {
		{
			BooleanExpression predicate = airline.name.lt(lufthansa.getName());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result,
							Arrays.stream(containsName).filter(a -> compareTo(a, lufthansa) < 0).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name < $1", bq(predicate));
		}
	}

	@Test
	void testGt() {
		{
			BooleanExpression predicate = airline.name.gt(lufthansa.getName());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result,
							Arrays.stream(containsName).filter(a -> compareTo(a, lufthansa) > 0).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name > $1", bq(predicate));
		}
	}

	@Test
	void testLoe() {
		{
			BooleanExpression predicate = airline.name.loe(lufthansa.getName());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result,
							Arrays.stream(containsName).filter(a -> compareTo(a, lufthansa) <= 0).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name <= $1", bq(predicate));
		}
	}

	@Test
	void testGoe() {
		{
			BooleanExpression predicate = airline.name.goe(lufthansa.getName());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(
					comprises(result,
							Arrays.stream(containsName).filter(a -> compareTo(a, lufthansa) >= 0).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE name >= $1", bq(predicate));
		}
	}

	// when hqCountry == null, no value is stored therefore "hqCountry is null" is false. Only "hqCountry":null gives
	// "hqCountry is null" as true - and it is not possible to store that the spring data implementation. Conversely, only
	// when hqCountry has a value (which is not 'null') gives "is not null" so isNull and isNotNull are *not* compliments
	@Test
	void testIsNull() {
		{
			BooleanExpression predicate = airline.hqCountry.isNull();
			Optional<Airline> result = airlineRepository.findOne(predicate);
			// assertNull(exactly(result, nullStringAirline), "[unexpected] -> [missing]");
			assertEquals(" WHERE hqCountry is null", bq(predicate));
		}
	}

	// when hqCountry == null, no value is stored therefore isNull is false. Only hqCountry:null gives isNull
	// and we don't have that. Conversely, only hqCountry has a value (which is not 'null') gives isNotNull
	// so isNull and isNotNull are *not* compliments
	@Test
	void testIsNotNull() {
		{
			BooleanExpression predicate = airline.hqCountry.isNotNull();
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, Arrays.stream(saved).filter(a -> a.getHqCountry() != null).toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE hqCountry is not null", bq(predicate));
		}
	}

	@Test
	void testContainsKey() {
		{
			MapPath mapPath = Expressions.mapPath(Airline.class, Airline.class, null, airline.getMetadata());
			BooleanExpression predicate = mapPath.containsKey(Expressions.asString("name"));
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result, containsName), "[unexpected] -> [missing]");
			assertEquals(" WHERE name is not missing", bq(predicate));
		}
	}

	@Test
	void testStringLength() {
		{
			BooleanExpression predicate = airline.name.length().eq(united.getName().length());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result,
					Arrays.stream(containsName)
							.filter(a -> a.getName() != null && a.getName().length() == united.getName().length())
							.toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE LENGTH(name) = $1", bq(predicate));
		}
		{
			BooleanExpression predicate = airline.name.length().eq(flyByNight.getName().length());
			Iterable<Airline> result = airlineRepository.findAll(predicate);
			assertNull(comprises(result,
					Arrays.stream(containsName)
							.filter(a -> a.getName() != null && a.getName().length() == flyByNight.getName().length())
							.toArray(Airline[]::new)),
					"[unexpected] -> [missing]");
			assertEquals(" WHERE LENGTH(name) = $1", bq(predicate));
		}
	}

	/**
	 * The intention of join() and on() is not clear.
	 * The implementation does a join that seems consistent with querydsl join - but it's not what I expected.
	 */
	@Test
	void testJoin() {
		{
			// I was expecting the left-outer join to be used with NEST like this:
			// SELECT meta(airline).id as __id, * FROM `my_bucket` airline NEST my_bucket homeairport on
			// homeairport.homeOfAirlineId = meta(airline).id where UPPER(airline.name)=$1

			// There are two airlines that match name.equalsIgnoreCase(united.getName())
			// but only one of those airlines has jfk as its homeAirport.

			// A predicate for the LHS.
			BooleanExpression predicate = airline.name.equalsIgnoreCase(united.getName());
			// this isn't what the 'join' is 'on'. The 'on' is implicitly by id:
			// airlinewithhomeairport.homeairport.id = airport.id
			BooleanExpression rhsPredicate = QAirport.airport.iata.eq("jfk");
			List<AirlineWithHomeAirport> result = new SpringDataCouchbaseQuery<>((CouchbaseOperations) (airlineRepository
					.getOperations()), AirlineWithHomeAirport.class).where(predicate).join(
							QAirport.airport.id /* <- populated by these docs  */, /* source (?) */
							QAirlineWithHomeAirport.airlineWithHomeAirport.homeAirport.homeOfAirlineId /* <- this element... */ /* target(?) */)
							.on(rhsPredicate).fetch();
			System.out.println(result);
			assertEquals(1,result.size(), "should have returned unitedLowercase");
			assertEquals( unitedLowercase, result.get(0));
		}

	}

	private void sleep(int millis) {
		try {
			Thread.sleep(millis); // so they are executed out-of-order
		} catch (InterruptedException ie) {
			;
		}
	}

	int compareTo(Airline a1, Airline a2) {
		String s1 = a1 != null ? a1.getName() : null;
		String s2 = a2 != null ? a2.getName() : null;
		if (s1 == null && s2 == null) {
			return 0;
		} else if (s1 == null) {
			return -1;
		} else if (s2 == null) {
			return 1;
		} else {
			return s1.compareTo(s2);
		}
	}

	boolean equals(Airline a1, Airline a2) {
		String s1 = a1 != null ? a1.getName() : null;
		String s2 = a2 != null ? a2.getName() : null;
		return (s1 == null && s2 == null) || (s1 != null && s1.equals(s2));
	}

	boolean equalsIC(Airline a1, Airline a2) {
		String s1 = a1 != null ? a1.getName() : null;
		String s2 = a2 != null ? a2.getName() : null;
		return (s1 == null && s2 == null) || (s1 != null && s1.equalsIgnoreCase(s2));
	}

	boolean contains(Airline a1, String s2) {
		String s1 = a1 != null ? a1.getName() : null;
		return (s1 == null && s2 == null) || (s1 != null && s1.contains(s2));
	}

	boolean containsIC(Airline a1, String s2) {
		String s1 = a1 != null ? a1.getName() : null;
		s1 = s1 != null ? s1.toUpperCase(Locale.ROOT) : null;
		s2 = s2 != null ? s2.toUpperCase(Locale.ROOT) : null;
		return (s1 == null && s2 == null) || (s1 != null && s1.contains(s2));
	}

	boolean matches(Airline a1, String s2) {
		String s1 = a1 != null ? a1.getName() : null;
		return (s1 == null && s2 == null) || (s1 != null && s1.matches(s2));
	}

	boolean matchesIC(Airline a1, String s2) {
		String s1 = a1 != null ? a1.getName() : null;
		s1 = s1 != null ? s1.toUpperCase(Locale.ROOT) : null;
		s2 = s2 != null ? s2.toUpperCase(Locale.ROOT) : null;
		return (s1 == null && s2 == null) || (s1 != null && s1.matches(s2));
	}

	boolean startsWith(Airline a1, String s2) {
		String s1 = a1 != null ? a1.getName() : null;
		return (s1 == null && s2 == null) || (s1 != null && s1.startsWith(s2));
	}

	boolean startsWithIC(Airline a1, String s2) {
		String s1 = a1 != null ? a1.getName() : null;
		s1 = s1 != null ? s1.toUpperCase(Locale.ROOT) : null;
		s2 = s2 != null ? s2.toUpperCase(Locale.ROOT) : null;
		return (s1 == null && s2 == null) || (s1 != null && s1.startsWith(s2));
	}

	boolean endsWith(Airline a1, String s2) {
		String s1 = a1 != null ? a1.getName() : null;
		return (s1 == null && s2 == null) || (s1 != null && s1.endsWith(s2));
	}

	boolean endsWithIC(Airline a1, String s2) {
		String s1 = a1 != null ? a1.getName() : null;
		s1 = s1 != null ? s1.toUpperCase(Locale.ROOT) : null;
		s2 = s2 != null ? s2.toUpperCase(Locale.ROOT) : null;
		return (s1 == null && s2 == null) || (s1 != null && s1.endsWith(s2));
	}

	@Configuration
	@EnableCouchbaseRepositories("org.springframework.data.couchbase")
	@EnableCouchbaseAuditing(dateTimeProviderRef = "dateTimeProviderRef")
	static class Config extends AbstractCouchbaseConfiguration {

		@Override
		public String getConnectionString() {
			return connectionString();
		}

		@Override
		public String getUserName() {
			return config().adminUsername();
		}

		@Override
		public String getPassword() {
			return config().adminPassword();
		}

		@Override
		public String getBucketName() {
			return bucketName();
		}

		@Bean(name = "auditorAwareRef")
		public NaiveAuditorAware testAuditorAware() {
			return new NaiveAuditorAware();
		}

		@Override
		public void configureEnvironment(final ClusterEnvironment.Builder builder) {
			builder.ioConfig().maxHttpConnections(11).idleHttpConnectionTimeout(Duration.ofSeconds(4));
			return;
		}

		@Bean(name = "dateTimeProviderRef")
		public DateTimeProvider testDateTimeProvider() {
			return new AuditingDateTimeProvider();
		}

		@Bean
		public LocalValidatorFactoryBean validator() {
			return new LocalValidatorFactoryBean();
		}

		@Bean
		public ValidatingCouchbaseEventListener validationEventListener() {
			return new ValidatingCouchbaseEventListener(validator());
		}
	}

	String bq(Predicate predicate) {
		BasicQuery basicQuery = new BasicQuery((QueryCriteriaDefinition) serializer.handle(predicate), null);
		return basicQuery.export(new int[1]);
	}

	@Configuration
	@EnableCouchbaseRepositories("org.springframework.data.couchbase")
	@EnableCouchbaseAuditing(auditorAwareRef = "auditorAwareRef", dateTimeProviderRef = "dateTimeProviderRef")
	static class ConfigRequestPlus extends AbstractCouchbaseConfiguration {

		@Override
		public String getConnectionString() {
			return connectionString();
		}

		@Override
		public String getUserName() {
			return config().adminUsername();
		}

		@Override
		public String getPassword() {
			return config().adminPassword();
		}

		@Override
		public String getBucketName() {
			return bucketName();
		}

		@Bean(name = "auditorAwareRef")
		public NaiveAuditorAware testAuditorAware() {
			return new NaiveAuditorAware();
		}

		@Bean(name = "dateTimeProviderRef")
		public DateTimeProvider testDateTimeProvider() {
			return new AuditingDateTimeProvider();
		}

		@Override
		public QueryScanConsistency getDefaultConsistency() {
			return REQUEST_PLUS;
		}
	}
}
