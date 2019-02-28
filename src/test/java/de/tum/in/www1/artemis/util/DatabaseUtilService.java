package de.tum.in.www1.artemis.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import de.tum.in.www1.artemis.service.compass.assessment.ModelElementAssessment;
import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Service
public class DatabaseUtilService {
  @Autowired CourseRepository courseRepo;
  @Autowired ExerciseRepository exerciseRepo;
  @Autowired UserRepository userRepo;
  @Autowired ResultRepository resultRepo;
  @Autowired ParticipationRepository participationRepo;
  @Autowired ModelingSubmissionRepository modelingSubmissionRepo;
  @Autowired ModelingSubmissionService modelSubmissionService;
  @Autowired ObjectMapper mapper;

  private static ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);
  private static ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);
  private static ZonedDateTime futureFututreTimestamp = ZonedDateTime.now().plusDays(2);

  public void resetDatabase() {
    participationRepo.deleteAll();
    exerciseRepo.deleteAll();
    courseRepo.deleteAll();
    userRepo.deleteAll();
    assertThat(courseRepo.findAll()).as("course data has been cleared").isEmpty();
    assertThat(exerciseRepo.findAll()).as("exercise data has been cleared").isEmpty();
    assertThat(userRepo.findAll()).as("user data has been cleared").isEmpty();
  }

  public void resetFileStorage() throws IOException {
    Path path = Paths.get(Constants.FILEPATH_COMPASS + File.separator);
    FileUtils.cleanDirectory(new File(path.toUri()));
  }

  public Participation addParticipationForExercise(Exercise exercise, String login) {
    User user =
        userRepo
            .findOneByLogin(login)
            .orElseThrow(
                () -> new IllegalArgumentException("Provided login does not exist in database"));
    Participation participation = new Participation();
    participation.setStudent(user);
    participation.setExercise(exercise);
    participationRepo.save(participation);
    Participation storedParticipation =
        participationRepo.findOneByExerciseIdAndStudentLogin(exercise.getId(), login);
    assertThat(storedParticipation).isNotNull();

    return participationRepo
        .findByIdWithEagerSubmissionsAndEagerResultsAndEagerAssessors(storedParticipation.getId())
        .get();
  }

  public void addUsers(int numberOfStudents, int numberOfTutors) {
    LinkedList<User> students =
        ModelGenrator.generateActivatedUsers("student", new String[] {"tumuser"}, numberOfStudents);
    LinkedList<User> tutors =
        ModelGenrator.generateActivatedUsers("tutor", new String[] {"tutor"}, numberOfTutors);
    LinkedList<User> usersToAdd = new LinkedList<>();
    usersToAdd.addAll(students);
    usersToAdd.addAll(tutors);
    userRepo.saveAll(usersToAdd);
    assertThat(userRepo.findAll().size())
        .as("all users are created")
        .isEqualTo(numberOfStudents + numberOfTutors);
    assertThat(userRepo.findAll())
        .as("users are correctly stored")
        .containsAnyOf(usersToAdd.toArray(new User[0]));
  }

  public ModelingSubmission addModelingSubmissionForAssessment(
      ModelingExercise exercise, String model, String login) {
    Participation participation = addParticipationForExercise(exercise, login);
    ModelingSubmission submission = new ModelingSubmission(true, model);
    participation.setSubmissions(
        (Set<Submission>) Hibernate.unproxy(participation.getSubmissions()));
    submission = modelSubmissionService.save(submission, exercise, participation);
    Result result = new Result();
    result.setSubmission(submission);
    submission.setResult(result);
    submission.getParticipation().addResult(result);
    resultRepo.save(result);
    modelingSubmissionRepo.save(submission);
    return submission;
  }

  public void addCourseWithModelingExercise() {
    Course course =
        ModelGenrator.generateCourse(
            null,
            pastTimestamp,
            futureFututreTimestamp,
            new HashSet<>(),
            "tumuser",
            "tutor",
            "tutor");
    ModelingExercise exercise =
        ModelGenrator.generateModelingExercise(
            pastTimestamp, futureTimestamp, futureFututreTimestamp, course);
    course.addExercises(exercise);
    courseRepo.save(course);
    exerciseRepo.save(exercise);
    List<Course> courseRepoContent = courseRepo.findAllWithEagerExercises();
    List<Exercise> exerciseRepoContent = exerciseRepo.findAll();
    assertThat(exerciseRepoContent.size()).as("a exercise got stored").isEqualTo(1);
    assertThat(courseRepoContent.size()).as("a course got stored").isEqualTo(1);
    assertThat(courseRepoContent.get(0).getExercises().size())
        .as("Course contains exercise")
        .isEqualTo(1);
    assertThat(courseRepoContent.get(0).getExercises().contains(exerciseRepoContent.get(0)))
        .as("course contains the right exercise")
        .isTrue();
  }

  /**
   * @param path path relative to the test resources folder
   * @return string representation of given file
   * @throws Exception
   */
  public String loadFileFromResources(String path) throws Exception {
    java.io.File file = ResourceUtils.getFile("classpath:" + path);
    StringBuilder builder = new StringBuilder();
    Files.lines(file.toPath()).forEach(builder::append);
    assertThat(builder.toString()).as("model has been correctly read from file").isNotEqualTo("");
    return builder.toString();
  }

  public List<ModelElementAssessment> loadAssessmentFomRessources(String path) throws Exception {
    String fileContent = loadFileFromResources(path);
    fileContent = fileContent.replaceFirst("\\{\"assessments\":", "");
    fileContent = fileContent.substring(0, fileContent.length() - 1);
    return mapper.readValue(
        fileContent,
        mapper.getTypeFactory().constructCollectionType(List.class, ModelElementAssessment.class));
  }

  public void checkSubmissionCorrectlyStored(
      Long studentId, Long exerciseId, Long submissionId, String sentModel) throws Exception {
    JsonObject storedModel = modelSubmissionService.getModel(exerciseId, studentId, submissionId);
    JsonParser parser = new JsonParser();
    JsonObject sentModelObject = parser.parse(sentModel).getAsJsonObject();
    assertThat(storedModel).as("model correctly stored").isEqualTo(sentModelObject);
  }
}
