package org.igov.service.business.action.task.bp.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.impl.util.json.JSONObject;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.task.Task;
import org.apache.commons.lang3.StringUtils;
import org.igov.io.GeneralConfig;
import org.igov.io.web.HttpRequester;
import org.igov.model.action.event.HistoryEvent_Service_StatusType;
import org.igov.model.escalation.EscalationHistory;
import org.igov.service.business.action.event.HistoryEventService;
import org.igov.service.business.action.task.bp.BpService;
import org.igov.service.business.escalation.EscalationHistoryService;
import org.igov.service.business.place.PlaceService;
import org.igov.service.exchange.SubjectCover;
import org.igov.util.ToolLuna;
import org.igov.util.JSON.JsonRestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author OlgaPrylypko
 * @since 2015-12-01.
 */
@Service
public class BpServiceHandler {

    public static Map<String, String> mGuideTaskParamKey = new TreeMap<>();
    public static final String PROCESS_ESCALATION = "system_escalation";
    public static final String PROCESS_FEEDBACK = "system_feedback";
    private static final String ESCALATION_FIELD_NAME = "nID_Proccess_Escalation";
    private static final String BEGIN_GROUPS_PATTERN = "${";
    private static final String END_GROUPS_PATTERN = "}";
    //private static final String INDIRECTLY_GROUP_PREFIX = "Indirectly_";
    private static final String ORDER_HISTORY_URL = "/search?sID_Order="; // #1350 п.11 <a href="URL">текст ссылки</a>

    private static final Logger LOG = LoggerFactory.getLogger(BpServiceHandler.class);
    @Autowired
    private GeneralConfig generalConfig;
    @Autowired
    private EscalationHistoryService escalationHistoryService;
    @Autowired
    private BpService bpService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private HistoryEventService historyEventService;
    @Autowired
    private TaskService taskService;
    @Autowired
    SubjectCover subjectCover;
    @Autowired
    private HttpRequester httpRequester;
    @Autowired
    private PlaceService placeService;
    /**
     * Текущее количество генерируемых заявок
     */
    private static Long feedBackCount = 0L;

    public static Long getFeedBackCount() {
        return feedBackCount;
    }

    public static void setFeedBackCount(Long feedBackCount) {
        BpServiceHandler.feedBackCount = feedBackCount;
    }

    /* String snID_Proccess_Feedback = bpHandler
                                  .startFeedbackProcess(snID_Task, snID_Process, sProcessName);*/
    public String startFeedbackProcess(String sID_task, String snID_Process, String processName) throws Exception {
        Map<String, Object> variables = new HashMap<>();
        variables.put("processName", processName);
        Integer nID_Server = generalConfig.getSelfServerId();
        String sID_Order = generalConfig.getOrderId_ByProcess(Long.valueOf(snID_Process));
        LOG.info("get sID_Order:(sID_Order={})", sID_Order);
        //get process variables
        HistoricTaskInstance details = historyService
                .createHistoricTaskInstanceQuery()
                .includeProcessVariables().taskId(sID_task)
                .singleResult();
        String feedbackProcessId  = null;
        if (details != null && details.getProcessVariables() != null) {
            Map<String, Object> processVariables = details.getProcessVariables();
            variables.put("nID_Protected", "" + ToolLuna.getProtectedNumber(Long.valueOf(snID_Process)));
            variables.put("bankIdlastName", processVariables.get("bankIdlastName"));
            variables.put("bankIdfirstName", processVariables.get("bankIdfirstName"));
            variables.put("bankIdmiddleName", processVariables.get("bankIdmiddleName"));
            variables.put("phone", "" + processVariables.get("phone"));
            variables.put("email", processVariables.get("email"));
            variables.put("sLoginAssigned", processVariables.get("sLoginAssigned"));
            variables.put("Place", placeService.getPlaceByProcess(snID_Process));
            variables.put("clfio", processVariables.get("bankIdlastName") + " "+processVariables.get("bankIdfirstName")+ " "+processVariables.get("bankIdmiddleName"));
            variables.put("region", processVariables.get("region"));
            variables.put("info", processVariables.get("info"));
            variables.put("nasPunkt", processVariables.get("nasPunkt"));
            
            variables.put("sBody", processVariables.get("sBody"));
            variables.put("sEmployeeContacts", processVariables.get("sEmployeeContacts"));
            variables.put("sBody_Indirectly", processVariables.get("sBody_Indirectly"));
            variables.put("nID_Rate_Indirectly", processVariables.get("nID_Rate_Indirectly"));
            Set<String> organ = getCandidateGroups(processName, sID_task, processVariables);
            variables.put("organ", organ.isEmpty() ? "" : organ.toString().substring(1, organ.toString().length() - 1));
            setSubjectParams(sID_task, processName, variables, processVariables);
            try {//issue 1006
                String jsonHistoryEvent = historyEventService.getHistoryEvent(sID_Order);
                LOG.info("get history event for bp:(jsonHistoryEvent={})", jsonHistoryEvent);
                JSONObject historyEvent = new JSONObject(jsonHistoryEvent);
                variables.put("nID_Rate", historyEvent.get("nRate"));
                variables.put("sDate_BP", historyEvent.get("sDate"));
                nID_Server = historyEvent.getInt("nID_Server");
            } catch (Exception oException) {
                LOG.error("ex!: {}", oException.getMessage());
                LOG.debug("FAIL:", oException);

            }

            
        try {
        	String feedbackProcessIdJson = bpService.startProcessInstanceByKey(nID_Server, PROCESS_FEEDBACK, variables);
            feedbackProcessId = new JSONObject(feedbackProcessIdJson).get("id").toString();
            variables.put("nID_Proccess_Feedback", feedbackProcessId);
           
            LOG.info(String.format(" >> start feedbackProcess [%s] ", feedbackProcessId));
        } catch (Exception oException) {
            LOG.error("error during starting feedback process!: {}", oException.getMessage());
            LOG.debug("FAIL:", oException);
        }
        return feedbackProcessId;
        }
		return feedbackProcessId;
       
    }

    public String startFeedbackProcessNew(String snID_Process) throws Exception {
        String feedbackProcessId = null;
            
            Map<String, Object> variables = new HashMap<>();
            Integer nID_Server = generalConfig.getSelfServerId();
           
            List<HistoricTaskInstance> details = historyService
                    .createHistoricTaskInstanceQuery()
                    .includeProcessVariables().taskId(snID_Process)
                    .list();
            String sID_Order = generalConfig.getOrderId_ByProcess(Long.valueOf(details.get(0).getProcessInstanceId()));
            
            HistoricProcessInstance oHistoricProcessInstance
            = historyService.createHistoricProcessInstanceQuery().processInstanceId(snID_Process).singleResult();
    ProcessDefinition oProcessDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(oHistoricProcessInstance.getProcessDefinitionId()).singleResult();
    LOG.info("oHistoricProcessInstanceeeeeee  ",oHistoricProcessInstance.getProcessVariables().get("bankIdlastName"));
    String sProcessName = oProcessDefinition.getName() != null ? oProcessDefinition.getName() : "";
    LOG.info("sProcessNameeeeeeeeeeeee  ", sProcessName);
    variables.put("processName", sProcessName);
            if (details != null && details.get(0).getProcessVariables() != null) {
           // variables.put("processName", details.get(0).getProcessDefinitionId());
            
            Map<String, Object> processVariables = details.get(0).getProcessVariables();
            variables.put("nID_Proccess_Feedback", snID_Process);
            LOG.info("nID_Proccess_Feedbackkkkkkk ", snID_Process);
            variables.put("nID_Protected", "" + ToolLuna.getProtectedNumber(Long.valueOf(details.get(0).getProcessInstanceId())));
            variables.put("clfio", processVariables.get("bankIdlastName") + " "+processVariables.get("bankIdfirstName")+ " "+processVariables.get("bankIdmiddleName"));
            LOG.info("bankIdlastNameeeeeeeeeeee ", processVariables.get("bankIdlastName"));
            LOG.info("bankIdfirstNameeeeeeeeeeeeeee ", processVariables.get("bankIdfirstName"));
            LOG.info("bankIdmiddleNameeeeeeeeeeeeeeee ", processVariables.get("bankIdmiddleName"));
            variables.put("phone", "" + processVariables.get("phone") != null ? String.valueOf(processVariables.get("phone")) : null);
            variables.put("email", processVariables.get("email") != null ? String.valueOf(processVariables.get("email")) : null);
            variables.put("Place", placeService.getPlaceByProcess(details.get(0).getProcessInstanceId()));
            variables.put("region", processVariables.get("region")); 
            variables.put("info", processVariables.get("info"));
            variables.put("nasPunkt", placeService.getPlaceByProcess(details.get(0).getProcessInstanceId()));
            variables.put("sBody", processVariables.get("sBody"));
            variables.put("sEmployeeContacts", processVariables.get("sEmployeeContacts"));
            variables.put("sBody_Indirectly", processVariables.get("sBody_Indirectly")); 
            variables.put("nID_Rate_Indirectly", processVariables.get("nID_Rate_Indirectly"));
            Set<String> organ = new TreeSet<>();
            Set<String> sLoginAssigned = new TreeSet<>();
            //get process variables
            for (HistoricTaskInstance task : details) {
                organ.addAll(getCandidateGroups(task.getProcessDefinitionId(), task.getId(), processVariables));
                sLoginAssigned.add(task.getAssignee());
            }
            LOG.info("get organ:(organ={})", organ);
            variables.put("organ", organ.isEmpty() ? "" : organ.toString().substring(1, organ.toString().length() - 1));
            variables.put("sLoginAssigned", sLoginAssigned.isEmpty()?"":sLoginAssigned);
            for (HistoricTaskInstance task : details) {
                setSubjectParams(task.getId(), task.getProcessDefinitionId(), variables, processVariables);
            }
            LOG.info(String.format(" >> start process [%s] with params: %s", PROCESS_FEEDBACK, variables));

            try {//issue 1006
                String jsonHistoryEvent = historyEventService.getHistoryEvent(sID_Order);
                LOG.info("get history event for bp:(jsonHistoryEvent={})", jsonHistoryEvent);
                JSONObject historyEvent = new JSONObject(jsonHistoryEvent);
                variables.put("nID_Rate", historyEvent.get("nRate"));
                //variables.put("sDate_BP", historyEvent.get("sDate"));
                variables.put("sDate_BP", details.get(0).getStartTime());
                nID_Server = historyEvent.getInt("nID_Server");
            } catch (Exception oException) {
                LOG.error("ex!: {}", oException.getMessage());
                LOG.debug("FAIL:", oException);

            }

            try {
                String feedbackProcess = bpService.startProcessInstanceByKey(nID_Server, PROCESS_FEEDBACK, variables);
                feedbackProcessId = new JSONObject(feedbackProcess).get("id").toString();
              //  variables.put("nID_Proccess_Feedback", feedbackProcessId);
            } catch (Exception oException) {
                LOG.error("error during starting feedback process!: {}", oException.getMessage());
                LOG.debug("FAIL:", oException);
            }
           }
        return feedbackProcessId;
    }

    public void checkBpAndStartEscalationProcess(final Map<String, Object> mTaskParam) throws Exception {
        String snID_Process = (String) mTaskParam.get("sProcessInstanceId");
        String processName = (String) mTaskParam.get("sID_BP_full");
        Integer nID_Server = generalConfig.getSelfServerId();
        String sID_Order = generalConfig.getOrderId_ByProcess(Long.valueOf(snID_Process));
        mTaskParam.put("sURL_OrderHistory", generalConfig.getSelfHostCentral() + ORDER_HISTORY_URL + sID_Order);
        LOG.info("ORDER_HISTORY_URL + onID_Task" + generalConfig.getSelfHostCentral() + ORDER_HISTORY_URL + sID_Order);

        try {
            String jsonHistoryEvent = historyEventService.getHistoryEvent(sID_Order);
            LOG.info("get history event for bp: (jsonHistoryEvent={})", jsonHistoryEvent);
            JSONObject historyEvent = new JSONObject(jsonHistoryEvent);
            Object escalationId = historyEvent.get(ESCALATION_FIELD_NAME);
            if (!(escalationId == null || "null".equalsIgnoreCase(String.valueOf(escalationId).trim()))) {
                LOG.info(String.format("For bp [%s] escalation process (with id=%s) has already started!",
                        processName, escalationId));
                return;
            }
            nID_Server = historyEvent.getInt("nID_Server");
        } catch (Exception oException) {
            LOG.error("ex!: {}", oException.getMessage());
            LOG.debug("FAIL:", oException);
        }
        String taskName = (String) mTaskParam.get("sTaskName");
        String LoginAssigned = (String) mTaskParam.get("sLoginAssigned");
        LOG.info("Escalation task params: {}", mTaskParam);
        String escalationProcessId = startEscalationProcess(mTaskParam, snID_Process, processName, nID_Server);
        Map<String, String> params = new HashMap<>();
        params.put(ESCALATION_FIELD_NAME, escalationProcessId);
        LOG.info(" >>Start escalation process. (nID_Proccess_Escalation={})", escalationProcessId);
        try {
            LOG.info(" updateHistoryEvent: " + snID_Process + " taskName: " + taskName + " params: " + params);
            historyEventService.updateHistoryEvent(generalConfig.getOrderId_ByProcess(Long.valueOf(snID_Process)), taskName, false, HistoryEvent_Service_StatusType.OPENED_ESCALATION, params);
            EscalationHistory escalationHistory = escalationHistoryService.create(Long.valueOf(snID_Process),
                    Long.valueOf(mTaskParam.get("sTaskId").toString()),
                    Long.valueOf(escalationProcessId), EscalationHistoryService.STATUS_CREATED);
            LOG.info(" >> save to escalationHistory.. ok! (escalationHistory={})", escalationHistory);
        } catch (Exception oException) {
            LOG.error("ex!: {}", oException.getMessage());
            LOG.debug("FAIL:", oException);
        }
    }

    private String startEscalationProcess(final Map<String, Object> mTaskParam, final String sID_Process,
            final String sProcessName, Integer nID_Server) throws Exception {
        Map<String, Object> mParam = new HashMap<>();
        mParam.put("processID", sID_Process);
        mParam.put("processName", sProcessName);
        mParam.put("nID_Protected", "" + ToolLuna.getProtectedNumber(Long.valueOf(sID_Process)));
        mParam.put("bankIdfirstName", mTaskParam.get("bankIdfirstName"));
        mParam.put("bankIdmiddleName", mTaskParam.get("bankIdmiddleName"));
        mParam.put("bankIdlastName", mTaskParam.get("bankIdlastName"));
        mParam.put("sTaskIDPlusName", mTaskParam.get("sID_State_BP") + " - " + mTaskParam.get("sTaskName"));

        mParam.put("sTaskId", mTaskParam.get("sTaskId"));
        mGuideTaskParamKey.put("sTaskId", "ИД  таски");

        mParam.put("sEmployeeContacts", mTaskParam.get("sEmployeeContacts"));
        mGuideTaskParamKey.put("sEmployeeContacts", "ПІБ та контактні телефони відповідальних посадовців");
        mParam.put("nElapsedDays", mTaskParam.get("nElapsedDays"));
        mGuideTaskParamKey.put("nElapsedDays", "Заявка знаходиться на цій стадії");
        mParam.put("email", mTaskParam.get("email"));
        mGuideTaskParamKey.put("email", "email");
        mParam.put("phone", "" + mTaskParam.get("phone"));
        mGuideTaskParamKey.put("phone", "Контактний телефон громадянина");
        LOG.info("getPlaceByProcess(sID_Process): " + placeService.getPlaceByProcess("sID_Process") + " sID_Process: " + sID_Process);
        mParam.put("email", mTaskParam.get("email"));
        Map mTaskParamConverted = convertTaskParam(mTaskParam);
        String sField = convertTaskParamToString(mTaskParamConverted);
        LOG.info("mTaskParam={}, mTaskParamConverted={}", mTaskParam, mTaskParamConverted);
        LOG.info("sField={}", sField);
        mParam.put("saField", sField + ".");

        Set<String> organs = getCandidateGroups(sProcessName, mTaskParam.get("sTaskId").toString(), null);
        String organ = trimGroups(organs);
        LOG.info("organ: " + organ);
        mParam.put("organ", organ);
        mParam.put("sLoginAssigned", mTaskParam.get("sLoginAssigned"));
        mGuideTaskParamKey.put("sLoginAssigned", "Логин сотрудника");
        mParam.put("sNameProcess", mTaskParam.get("sServiceType"));
        mParam.put("sOrganName", mTaskParam.get("area"));
        mParam.put("sDate_BP", mTaskParam.get("sDate_BP"));
        mGuideTaskParamKey.put("sDate_BP", "Дата БП");
        mParam.put("sURL_OrderHistory", mTaskParam.get("sURL_OrderHistory"));
        mGuideTaskParamKey.put("sURL_OrderHistory", "Посилання на первинне звернення");
        mParam.put("Place", placeService.getPlaceByProcess(sID_Process));
        mGuideTaskParamKey.put("Place", "Обраний населений пункт");
        LOG.info("mParam: " + mParam);
        setSubjectParams(mTaskParam.get("sTaskId").toString(), sProcessName, mParam, null);
        LOG.info("START PROCESS_ESCALATION={}, with mParam={}", PROCESS_ESCALATION, mParam);
        String snID_ProcessEscalation = null;
        try {
            String soProcessEscalation = bpService.startProcessInstanceByKey(nID_Server, PROCESS_ESCALATION, mParam);
            snID_ProcessEscalation = new JSONObject(soProcessEscalation).get("id").toString();
        } catch (Exception oException) {
            LOG.error("during starting escalation process!: {}", oException.getMessage());
            LOG.debug("FAIL:", oException);
        }
        return snID_ProcessEscalation;
    }

    /**
     * organise groups into single string
     *
     * @param organs
     * @return
     */
    private String trimGroups(Set<String> organs) {
        if (organs.isEmpty()) {
            return "";
        }
        final String DELIMITER = ", ";

        StringBuilder result = new StringBuilder();
        for (String group : organs) {
            result.append(group);
            result.append(DELIMITER);
        }
        String res = result.toString();
        if (res.endsWith(DELIMITER)) {
            res = StringUtils.removeEnd(res, DELIMITER);
        }
        return res;
    }

//    private String getPlaceForProcess(String sID_Process) {
//        Map<String, String> param = new HashMap<String, String>();
//        param.put("nID_Process", sID_Process);
//        param.put("nID_Server", generalConfig.getSelfServerId().toString());
//        String sURL = generalConfig.getSelfHostCentral() + "/wf/service/object/place/getOrderPlaces"; 
//        LOG.info("(sURL={},mParam={})", sURL, param);
//        String soResponse = null;
//        try {
//            soResponse = httpRequester.getInside(sURL, param);
//            Map res = JsonRestUtils.readObject(soResponse, Map.class);
//            soResponse = (String) res.get("Place");
//        } catch (Exception ex) {
//            LOG.error("[getPlaceForProcess]: ", ex);
//        }
//        LOG.info("(soResponse={})", soResponse);
//        return soResponse;
//    }

    //TODO: Допилить и начать использовать PlaceServiceImpl вместо этого
   /* private String getPlaceByProcess(String sID_Process) {
        Map<String, String> mParam = new HashMap<String, String>();
        mParam.put("nID_Process", sID_Process);
        //LOG.info("2sID_Process: " + sID_Process);
        mParam.put("nID_Server", generalConfig.getSelfServerId().toString());
        //LOG.info("3generalConfig.getSelfServerId().toString(): " + generalConfig.getSelfServerId().toString());
        String sURL = generalConfig.getSelfHostCentral() + "/wf/service/object/place/getPlaceByProcess";
        //LOG.info("ssURL: " + sURL);
        LOG.info("(sURL={},mParam={})", sURL, mParam);
        String soResponse = null;
        String sName = null;
        try {
            soResponse = httpRequester.getInside(sURL, mParam);
            LOG.info("soResponse={}", soResponse);
            Map mReturn = JsonRestUtils.readObject(soResponse, Map.class);
            LOG.info("mReturn={}" + mReturn);
            sName = (String) mReturn.get("sName");
            LOG.info("sName={}", sName);
        } catch (Exception ex) {
            LOG.error("", ex);
        }
        //LOG.info("(soResponse={})", soResponse);
        return sName;//soResponse
    } */

    private Set<String> getCurrentCadidateGroup(final String sProcessName) {
        Set<String> asCandidateCroupToCheck = new HashSet<>();
        BpmnModel oBpmnModel = repositoryService.getBpmnModel(sProcessName);
        for (FlowElement oFlowElement : oBpmnModel.getMainProcess().getFlowElements()) {
            if (oFlowElement instanceof UserTask) {
                UserTask oUserTask = (UserTask) oFlowElement;
                List<String> asCandidateGroup = oUserTask.getCandidateGroups();
                if (asCandidateGroup != null && !asCandidateGroup.isEmpty()) {
                    asCandidateCroupToCheck.addAll(asCandidateGroup);
                }
            }
        }
        return asCandidateCroupToCheck;
    }

    private Set<String> getCandidateGroups(final String sProcessName, final String snID_Task,
            final Map<String, Object> mTaskVariable) {
        LOG.info("sProcessName: " + sProcessName + "snID_Task: " + snID_Task + "mTaskVariable: " + mTaskVariable);
        Set<String> asCandidateCroupToCheck = getCurrentCadidateGroup(sProcessName);
        LOG.info("asCandidateCroupToCheck: " + asCandidateCroupToCheck);
        String saCandidateCroupToCheck = asCandidateCroupToCheck.toString();
        //if (saCandidateCroupToCheck.contains(BEGIN_GROUPS_PATTERN)) {
        Map<String, Object> mProcessVariable = null;
        if (mTaskVariable == null) {//get process variables
            HistoricTaskInstance oHistoricTaskInstance = historyService
                    .createHistoricTaskInstanceQuery()
                    .includeProcessVariables().taskId(snID_Task)
                    .singleResult();
            if (oHistoricTaskInstance != null && oHistoricTaskInstance.getProcessVariables() != null) {
                mProcessVariable = oHistoricTaskInstance.getProcessVariables();
            }
        } else { //use existing process variables
            mProcessVariable = mTaskVariable;
        }
        if (mProcessVariable != null) {
            Set<String> asCandidateGroupNew = new HashSet<>();
            for (String sCandidateGroup : asCandidateCroupToCheck) {
                String sCandidateGroupNew = sCandidateGroup;
                if (sCandidateGroup.contains(BEGIN_GROUPS_PATTERN)) {
                    String sVariableName = StringUtils.substringAfter(sCandidateGroup, BEGIN_GROUPS_PATTERN);
                    sVariableName = StringUtils.substringBeforeLast(sVariableName, END_GROUPS_PATTERN);
                    Object sVariableValue = mProcessVariable.get(sVariableName);
                    if (sVariableValue != null) {
                        sCandidateGroupNew = sCandidateGroup.replace(BEGIN_GROUPS_PATTERN + sVariableName + END_GROUPS_PATTERN, "" + sVariableValue);
                        LOG.info("replace candidateGroups: from sCandidateGroup={}, to sCandidateGroupNew={}", sCandidateGroup, sCandidateGroupNew);
                    }
                }
                asCandidateGroupNew.add(sCandidateGroupNew);
            }
            asCandidateCroupToCheck = asCandidateGroupNew;
            saCandidateCroupToCheck = asCandidateGroupNew.toString();
        }
//        }
        return asCandidateCroupToCheck;
    }

    public String createServiceMessage(String taskId) {
        String jsonServiceMessage = "{}";
        Map<String, Object> processVariables = null;
        HistoricTaskInstance taskDetails = historyService
                .createHistoricTaskInstanceQuery()
                .includeProcessVariables().taskId(taskId)
                .singleResult();
        if (taskDetails != null && taskDetails.getProcessVariables() != null) {
            processVariables = taskDetails.getProcessVariables();
        }
        if (processVariables != null) {
            try {
                String snID_Process = processVariables.get("processID").toString();
                String sID_Order = generalConfig.getOrderId_ByProcess(Long.valueOf(snID_Process));
                String jsonHistoryEvent = historyEventService.getHistoryEvent(sID_Order);
                LOG.info("get history event for bp: (jsonHistoryEvent={})", jsonHistoryEvent);

                Map<String, String> params = new HashMap<>();
                params.put("sBody", "" + processVariables.get("sBody_Indirectly"));
                params.put("sData", "" + processVariables.get("saField"));
                params.put("nID_SubjectMessageType", "" + 3L);
                //params.put("sID_Order", new JSONObject(jsonHistoryEvent).getString("sID_Order"));
                params.put("sID_Order", sID_Order);
                LOG.info("try to save service message with params: (params={})", params);
                jsonServiceMessage = historyEventService.addServiceMessage(params);
                LOG.info("(jsonServiceMessage={})", jsonServiceMessage);
            } catch (Exception oException) {
                LOG.error("ex!: {}", oException.getMessage());
                LOG.debug("FAIL:", oException);
                jsonServiceMessage = "{error: " + oException.getMessage() + "}";
            }

        }
        return jsonServiceMessage;
    }

    public void setSubjectParams(String taskId, String sProcessName, Map<String, Object> mParam, Map processVariables) {
        try {
            String sResult = (String) mParam.get("sEmployeeContacts");
            Set<String> accounts = new HashSet<>();
            Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
            LOG.info("task: " + task);
          //TODO:раскомнтаить - ??????
            /*if (task.getAssignee() != null) {
                accounts.add(task.getAssignee());
            }*/
            accounts.addAll(getCandidateGroups(sProcessName, taskId, processVariables));
            LOG.info("accounts: " + accounts);
            Map<String, Map<String, Map>> result = subjectCover.getSubjectsBy(accounts);
            if (result != null && !result.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                LOG.info("result: " + result);
                List<Map<String, Object>> aSubjectAccount = (List<Map<String, Object>>) result.get("aSubjectAccount");
                for (Map<String, Object> subjectAccount : aSubjectAccount) {
                    Map<String, Object> subject = (Map<String, Object>) subjectAccount.get("oSubject");
                    String label = (String) subject.get("sLabel");
                    String accountContacts = getContact((List<Map>) subject.get("aSubjectAccountContact"));
                    sb.append(" ").append(label).append(" (").append(accountContacts).append(")");
                }
                LOG.info("sEmployeeContacts: " + sb.toString());
                mParam.put("sEmployeeContacts", sResult + sb.toString());

            }
        } catch (Exception ex) {
            LOG.error("[setSubjectParams]: ", ex);
        }
    }

    private String getContact(List<Map> aSubjectAccountContact) {
        StringBuilder sbContact = new StringBuilder();
        LOG.info("aSubjectAccountContact: " + aSubjectAccountContact);
        if (aSubjectAccountContact != null && !aSubjectAccountContact.isEmpty()) {
            for (Map subjectAccountContact : aSubjectAccountContact) {
                LOG.info("subjectAccountContact: " + subjectAccountContact);
                if (subjectAccountContact != null && !subjectAccountContact.isEmpty()
                        && subjectAccountContact.get("sValue") != null
                        && !"".equals(subjectAccountContact.get("sValue"))
                        && subjectAccountContact.get("oSubjectContactType") != null
                        && "Phone".equalsIgnoreCase((String) ((Map) subjectAccountContact.get("oSubjectContactType")).get("sName_EN"))) {
                    LOG.info("sValue: " + subjectAccountContact.get("sValue"));
                    sbContact.append(subjectAccountContact.get("sValue")).append("; ");
                }
            }
        }
        return sbContact.toString(); //
    }

    public static String convertTaskParamToString(Map<String, Object> mTaskParam) {

        // Получаем набор элементов
        Set<Map.Entry<String, Object>> aTaskParamEntrySet = mTaskParam.entrySet();
        String result = " ";

        // Отобразим набор
        for (Map.Entry<String, Object> taskParam : aTaskParamEntrySet) {
//            result += taskParam.getKey() + ": " + taskParam.getValue() + "\n\r";
            result += taskParam.getKey() + ": " + taskParam.getValue() + "\n\n";
        }
        LOG.info(result);
        return result;

    }

    public static Map<String, Object> convertTaskParam(Map<String, Object> mTaskParam) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> taskParam : mTaskParam.entrySet()) {
            String taskParamKey = taskParam.getKey();
            Object taskParamValue = taskParam.getValue();

            if (mGuideTaskParamKey.get(taskParamKey) != null) {
                String newKeyTaskParam = mGuideTaskParamKey.get(taskParamKey);
                if (!"Удалить".equalsIgnoreCase(newKeyTaskParam)) {
                    result.put(newKeyTaskParam, taskParamValue);
                }
            } else {
                result.put(taskParamKey, taskParamValue);
            }
        }
        return result;
    }
}
