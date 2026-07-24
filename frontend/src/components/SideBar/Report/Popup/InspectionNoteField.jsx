function InspectionNoteField({
  content,
  onChangeContent,
}) {
  return (
    <div className="inspection-popup-section">
      <h3 className="inspection-popup-title">참고 사항</h3>

      <textarea
        className="inspection-content-textarea"
        value={content}
        placeholder="내용을 입력해 주세요"
        onChange={onChangeContent}
      />
    </div>
  );
}

export default InspectionNoteField;