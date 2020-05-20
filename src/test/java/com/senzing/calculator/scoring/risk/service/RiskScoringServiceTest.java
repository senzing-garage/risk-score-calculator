package com.senzing.calculator.scoring.risk.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.senzing.listener.senzing.service.exception.ServiceExecutionException;
import com.senzing.listener.senzing.service.exception.ServiceSetupException;
import com.senzing.listener.senzing.service.g2.G2Service;
import com.senzing.calculator.scoring.risk.service.db.DatabaseService;
import com.senzing.calculator.scoring.risk.service.g2.G2ServiceExt;

import static org.mockito.Mockito.*;

import mockit.Mock;
import mockit.MockUp;

@RunWith(MockitoJUnitRunner.class)
public class RiskScoringServiceTest {

  private static String g2Config = null;
  
  private static final String CONFIG = "{\"iniFile\":\"/opt/senzing/etc/G2Module.ini\",\"jdbcConnection\":\"myDB\"}";

  private static final String INPUT_MESSAGE = "{\"DATA_SOURCE\":\"TEST\",\"RECORD_ID\":\"RECORD3\",\"AFFECTED_ENTITIES\":[{\"ENTITY_ID\":1,\"LENS_CODE\":\"DEFAULT\"}]}";
  private static final String ENTITY_MESSAGE_1 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"NAME\":[{\"FEAT_DESC\":\"STEVE SMITH\",\"LIB_FEAT_ID\":1,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"STEVE SMITH\",\"LIB_FEAT_ID\":1,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"PEOPLE\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":2,\"MATCH_LEVEL_CODE\":\"POSSIBLY_SAME\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":1,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  
  private static final String ENTITY_MESSAGE_2 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"NAME\":[{\"FEAT_DESC\":\"STEVE SMITH\",\"LIB_FEAT_ID\":1,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"STEVE SMITH\",\"LIB_FEAT_ID\":1,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"IMDM\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":2,\"MATCH_LEVEL_CODE\":\"POSSIBLY_SAME\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  
  private static final String ENTITY_MESSAGE_3 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"NAME\":[{\"FEAT_DESC\":\"STEVE SMITH\",\"LIB_FEAT_ID\":1,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"STEVE SMITH\",\"LIB_FEAT_ID\":1,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"PEOPLE\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  
  private static final String ENTITY_MESSAGE_4 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"NAME\":[{\"FEAT_DESC\":\"STEVE SMITH\",\"LIB_FEAT_ID\":1,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"STEVE SMITH\",\"LIB_FEAT_ID\":1,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"DRLIC\":[{\"FEAT_DESC\":\"1234567890123\",\"LIB_FEAT_ID\":50,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"1234567890123\",\"LIB_FEAT_ID\":50,\"USED_FOR_CAND\":\"Y\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":1,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]},{\"FEAT_DESC\":\"9876543210987\",\"LIB_FEAT_ID\":51,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"9876543210987\",\"LIB_FEAT_ID\":51,\"USED_FOR_CAND\":\"Y\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":1,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"PEOPLE\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  
  private static final String ENTITY_MESSAGE_5 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"NAME\":[{\"FEAT_DESC\":\"STEVE SMITH\",\"LIB_FEAT_ID\":1,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"STEVE SMITH\",\"LIB_FEAT_ID\":1,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"DRLIC\":[{\"FEAT_DESC\":\"1234567890123\",\"LIB_FEAT_ID\":50,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"1234567890123\",\"LIB_FEAT_ID\":50,\"USED_FOR_CAND\":\"Y\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"PEOPLE\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  
  private static final String ENTITY_MESSAGE_6 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"DOB\":[{\"FEAT_DESC\":\"01-01-2000\",\"LIB_FEAT_ID\":70,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"01-01-2000\",\"LIB_FEAT_ID\":70,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"PEOPLE\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":2,\"MATCH_LEVEL_CODE\":\"POSSIBLY_SAME\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  
  private static final String ENTITY_MESSAGE_7 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"DOB\":[{\"FEAT_DESC\":\"01-01-2000\",\"LIB_FEAT_ID\":70,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"01-01-2000\",\"LIB_FEAT_ID\":70,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]},{\"FEAT_DESC\":\"01-01-1990\",\"LIB_FEAT_ID\":71,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"01-01-1990\",\"LIB_FEAT_ID\":71,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"PEOPLE\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":2,\"MATCH_LEVEL_CODE\":\"POSSIBLY_SAME\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  
  private static final String ENTITY_MESSAGE_8 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"SSN\":[{\"FEAT_DESC\":\"123-45-6789\",\"LIB_FEAT_ID\":70,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"123-45-6789\",\"LIB_FEAT_ID\":70,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"PEOPLE\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":2,\"MATCH_LEVEL_CODE\":\"POSSIBLY_SAME\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  
  private static final String ENTITY_MESSAGE_9 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"SSN\":[{\"FEAT_DESC\":\"123-45-6789\",\"LIB_FEAT_ID\":70,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"123-45-6789\",\"LIB_FEAT_ID\":70,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]},{\"FEAT_DESC\":\"789-56-1234\",\"LIB_FEAT_ID\":71,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"789-56-1234\",\"LIB_FEAT_ID\":71,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"PEOPLE\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":2,\"MATCH_LEVEL_CODE\":\"POSSIBLY_SAME\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  
  private static final String ENTITY_MESSAGE_10 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"DOB\":[{\"FEAT_DESC\":\"01-01-2000\",\"LIB_FEAT_ID\":70,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"01-01-2000\",\"LIB_FEAT_ID\":70,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"SSN\":[{\"FEAT_DESC\":\"123-45-6789\",\"LIB_FEAT_ID\":80,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"123-45-6789\",\"LIB_FEAT_ID\":80,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"IMDM\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":2,\"MATCH_LEVEL_CODE\":\"POSSIBLY_SAME\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  
  private static final String ENTITY_MESSAGE_11 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"DOB\":[{\"FEAT_DESC\":\"01-01-2000\",\"LIB_FEAT_ID\":70,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"01-01-2000\",\"LIB_FEAT_ID\":70,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"SSN\":[{\"FEAT_DESC\":\"123-45-6789\",\"LIB_FEAT_ID\":80,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"123-45-6789\",\"LIB_FEAT_ID\":80,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"IMDM\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":2,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  
  private static final String ENTITY_MESSAGE_12 = "{\"RESOLVED_ENTITY\":{\"ENTITY_ID\":5,\"LENS_CODE\":\"DEFAULT\",\"FEATURES\":{\"ADDRESS\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"UTYPE_CODE\":\"PRIMARY\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"808 STAR COURT LAS VEGAS NV 89111\",\"LIB_FEAT_ID\":3,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"DOB\":[{\"FEAT_DESC\":\"01-01-2000\",\"LIB_FEAT_ID\":70,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"01-01-2000\",\"LIB_FEAT_ID\":70,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":3,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"RISK_SCORE_OVERRIDE\":[{\"FEAT_DESC\":\"Red\",\"LIB_FEAT_ID\":105}],\"SSN\":[{\"FEAT_DESC\":\"123-45-6789\",\"LIB_FEAT_ID\":80,\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"123-45-6789\",\"LIB_FEAT_ID\":80,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"Y\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}],\"REL_LINK\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"UTYPE_CODE\":\"OWNER-OF\",\"FEAT_DESC_VALUES\":[{\"FEAT_DESC\":\"OWNERSHIP 1003-1\",\"LIB_FEAT_ID\":33,\"USED_FOR_CAND\":\"N\",\"USED_FOR_SCORING\":\"N\",\"ENTITY_COUNT\":2,\"CANDIDATE_CAP_REACHED\":\"N\",\"SCORING_CAP_REACHED\":\"N\",\"SUPPRESSED\":\"N\"}]}]},\"RECORDS\":[{\"JSON_DATA\":{\"RECORD_ID\":\"1003-1\"},\"DATA_SOURCE\":\"IMDM\",\"ENTITY_TYPE\":\"PEOPLE\"}]},\"RELATED_ENTITIES\":[{\"ENTITY_ID\":2001,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":2,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+NAME+ADDRESS (Ambiguous)\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CNAME_CFF\",\"REF_SCORE\":6,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"STEVE SMITH\"},{\"ENTITY_ID\":2,\"LENS_CODE\":\"DEFAULT\",\"MATCH_LEVEL\":3,\"MATCH_LEVEL_CODE\":\"POSSIBLY_RELATED\",\"MATCH_KEY\":\"+SURNAME+ADDRESS\",\"MATCH_SCORE\":\"12\",\"ERRULE_CODE\":\"CFF_SURNAME\",\"REF_SCORE\":4,\"IS_DISCLOSED\":0,\"IS_AMBIGUOUS\":0,\"ENTITY_NAME\":\"JENNY SMITH\"}]}";  

  @Before
  public void setupTest() throws IOException {
    String config = getConfig();
    new MockUp<G2Service>() {
      @Mock
      public void init(String iniFile) throws ServiceSetupException {
      }
      @Mock
      public String exportConfig() throws ServiceExecutionException {
        return config;
      }
    };
    new MockUp<G2ServiceExt>() {
      @Mock
      public void init(String iniFile) throws ServiceSetupException {
      }
      @Mock
      public String findEntitiesByFeatureIDs(List<Long> ids, long entityID) throws JSONException, ServiceExecutionException {
        return "[]";
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void init(String url) throws SQLException {
      }
    };
    new MockUp<G2ServiceExt>() {
      @Mock
      public void init(String iniFile) throws ServiceSetupException {
      }
    };
  }

  @Test
  public void processForAmbiguousRelationship() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_1;
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Red")));
        assertThat(collisionScore.toString(), is(equalTo("Red")));
        assertThat(reason.toString(), containsString("Ambiguous"));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  @Test
  public void processForIMDMDsrc() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_2;
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Yellow")));
        assertThat(collisionScore.toString(), is(equalTo("Yellow")));
        assertThat(reason.toString(), is(not(containsString("No iMDM record"))));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  @Test
  public void processWithNoPossibleMatch() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_3;
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Yellow")));
        assertThat(collisionScore.toString(), is(equalTo("Yellow")));
        assertThat(reason.toString(), is(not(containsString("Possible"))));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  @Test
  public void processWithMultipleExclisives() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_4;
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Red")));
        assertThat(collisionScore.toString(), is(equalTo("Red")));
        assertThat(reason.toString(), containsString("More than one F1E"));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  @Test
  public void processWithSharedExclisives() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_5;
      }
    };
    new MockUp<G2ServiceExt>() {
      @Mock
      public String findEntitiesByFeatureIDs(List<Long> ids, long entityID) throws JSONException, ServiceExecutionException {
        return "[{\"RES_ENTIT_ID\":120,\"UTYPE\":\"\",\"FTYPE_ID\":15}]";
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Red")));
        assertThat(collisionScore.toString(), is(equalTo("Red")));
        assertThat(reason.toString(), containsString("F1E or F1ES shared"));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  @Test
  public void processWithNoDOB() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_6;
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Yellow")));
        assertThat(collisionScore.toString(), is(equalTo("Yellow")));
        assertThat(reason.toString(), containsString("Not one and only one DOB"));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  @Test
  public void processWithTwoDOBs() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_7;
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Red")));
        assertThat(collisionScore.toString(), is(equalTo("Red")));
        assertThat(reason.toString(), containsString("More than one DOB"));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  @Test
  public void processWithNoSSN() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_8;
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Yellow")));
        assertThat(collisionScore.toString(), is(equalTo("Yellow")));
        assertThat(reason.toString(), containsString("Not one and only one SSN"));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  @Test
  public void processWithTwoSSNs() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_9;
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Yellow")));
        assertThat(collisionScore.toString(), is(equalTo("Yellow")));
        assertThat(reason.toString(), containsString("Not one and only one SSN"));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  @Test
  public void processHavingGreenDataQuality() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_10;
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Green")));
        assertThat(collisionScore.toString(), is(equalTo("Yellow")));
        assertThat(reason.toString(), containsString("At least 1 iMDM record"));
        assertThat(reason.toString(), containsString("Possible match exists"));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  @Test
  public void processHavingGreenCollision() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_11;
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Green")));
        assertThat(collisionScore.toString(), is(equalTo("Green")));
        assertThat(reason.toString(), containsString("Green"));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  @Test
  public void processWithRedOverride() throws ServiceExecutionException, ServiceSetupException {

    new MockUp<G2Service>() {
      @Mock
      public String getEntity(long g2EntiyId, boolean includeFullFeatures, boolean includeFeatureStats) throws ServiceExecutionException {
        return ENTITY_MESSAGE_12;
      }
    };
    new MockUp<DatabaseService>() {
      @Mock
      public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason) {
        assertThat(qualityScore.toString(), is(equalTo("Green")));
        assertThat(collisionScore.toString(), is(equalTo("Red")));
        assertThat(reason.toString(), containsString("\"Manually flagged red"));
      }
    };
    RiskScoringService service = new RiskScoringService();
    service.init(CONFIG);
    service.process(INPUT_MESSAGE);
  }

  private String getConfig() throws IOException {
    if (g2Config == null) {
      InputStream inputStream = this.getClass().getResourceAsStream("/g2config.json");
      g2Config = IOUtils.toString(inputStream);
    }
    return g2Config;
  }
}
